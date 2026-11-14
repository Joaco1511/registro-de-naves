import navesitas.Hu13 as H
import navesitas.Http as Http

import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.util.KeywordUtil

import java.net.URLEncoder

// =====================================================
// HU16_07 - SUBSANAR y DESISTIR no generan correos.
// =====================================================

String correoAgente = 'agente@navesitas.pa'

// -----------------------------------------------------
// Buscar mensajes por destinatario.
// -----------------------------------------------------

def buscarMensajes = { String correo ->

	RequestObject busqueda = new RequestObject('MailpitBuscar')

	String consulta = URLEncoder.encode('to:' + correo, 'UTF-8')

	busqueda.setRestUrl(
		'http://localhost:8025/api/v1/search?query=' + consulta
	)

	busqueda.setRestRequestMethod('GET')

	def respuesta = Http.enviar(busqueda)

	WS.verifyResponseStatusCode(respuesta, 200)

	return H.json(respuesta).messages ?: []
}

// -----------------------------------------------------
// Leer un mensaje de Mailpit.
// -----------------------------------------------------

def leerMensaje = { String id ->

	RequestObject consulta = new RequestObject('MailpitMensaje')

	consulta.setRestUrl(
		'http://localhost:8025/api/v1/message/' + id
	)

	consulta.setRestRequestMethod('GET')

	def respuesta = Http.enviar(consulta)

	WS.verifyResponseStatusCode(respuesta, 200)

	return H.json(respuesta)
}

// -----------------------------------------------------
// Obtener mensajes relacionados con una nave.
// -----------------------------------------------------

def mensajesPorNave = { String nombreNave ->

	Map encontrados = [:]

	buscarMensajes(correoAgente).each { resumen ->

		def detalle = leerMensaje(resumen.ID)
		String texto = detalle.Text ?: ''

		if (texto.contains(nombreNave)) {
			encontrados[resumen.ID.toString()] = detalle
		}
	}

	return encontrados
}

// -----------------------------------------------------
// Crear un expediente observado y esperar sus avisos.
// -----------------------------------------------------

def crearExpedienteObservado = {
	String prefijo,
	String agente,
	String dgmm ->

	String sufijo = System.currentTimeMillis().toString()
	String nombreNave = prefijo + ' ' + sufijo

	def naveId = H.nave(agente, [
		nombre: nombreNave,
		eslora: 24
	])

	assert naveId != null : 'No se pudo crear la nave.'

	H.preparar(agente, naveId)

	[
		'CERTIFICADO_PROPIEDAD',
		'PODER_NOTARIAL',
		'ITC'
	].each { tipo ->
		H.cargar(agente, naveId, tipo)
	}

	// DGMM admite el trámite.
	WS.verifyResponseStatusCode(
		H.accion(dgmm, naveId, 'ADMITIR'), 200
	)

	// DGMM observa el trámite.
	WS.verifyResponseStatusCode(
		H.pedir(
			dgmm,
			"/api/naves/${naveId}/tramite/estado",
			[
				accion: 'OBSERVAR',
				observacion: 'Prueba de notificacion HU16_07.'
			]
		),
		200
	)

	// Esperar los dos correos legítimos de DGMM.
	Map mensajesBase = [:]
	boolean admisionEncontrada = false
	boolean observacionEncontrada = false

	for (int intento = 0; intento < 12; intento++) {

		mensajesBase = mensajesPorNave(nombreNave)

		admisionEncontrada = mensajesBase.values().any {
			it.Text?.contains('Acción: Admitir trámite')
		}

		observacionEncontrada = mensajesBase.values().any {
			it.Text?.contains('Acción: Observar trámite')
		}

		if (admisionEncontrada && observacionEncontrada) {
			break
		}

		Thread.sleep(500)
	}

	assert admisionEncontrada :
		'No llegó el correo de admisión.'

	assert observacionEncontrada :
		'No llegó el correo de observación.'

	assert mensajesBase.size() == 2 :
		"Se esperaban dos avisos de DGMM, pero se encontraron ${mensajesBase.size()}."

	return [
		naveId: naveId,
		nombre: nombreNave,
		mensajesBase: mensajesBase
	]
}

// -----------------------------------------------------
// Comprobar que una acción del agente no agrega correos.
// -----------------------------------------------------

def verificarSinCorreoNuevo = {
	String nombreNave,
	Map mensajesBase ->

	Map mensajesDespues = mensajesBase

	// Esperar hasta 6 segundos para detectar envíos tardíos.
	for (int intento = 0; intento < 12; intento++) {

		mensajesDespues = mensajesPorNave(nombreNave)

		Thread.sleep(500)
	}

	Set<String> idsAntes = mensajesBase.keySet() as Set
	Set<String> idsDespues = mensajesDespues.keySet() as Set

	Set<String> nuevos = idsDespues - idsAntes

	assert nuevos.isEmpty() :
		"Se recibió un correo inesperado: ${nuevos}"

	assert idsDespues == idsAntes :
		'Cambió la cantidad de correos asociados con la nave.'

	KeywordUtil.logInfo(
		"Correos antes y después: ${idsDespues.size()}"
	)
}

// =====================================================
// 1. Iniciar sesiones.
// =====================================================

String agente = H.login(correoAgente)
String dgmm = H.login('revisor@navesitas.pa')

assert agente != null : 'No se pudo iniciar sesión como agente.'
assert dgmm != null : 'No se pudo iniciar sesión como DGMM.'

// =====================================================
// 2. PRIMER EXPEDIENTE: SUBSANAR.
// =====================================================

def casoSubsanar = crearExpedienteObservado(
	'HU16 Subsanar',
	agente,
	dgmm
)

KeywordUtil.logInfo(
	"Expediente para SUBSANAR: ${casoSubsanar.nombre}"
)

// El agente subsana.
def subsanacion = H.pedir(
	agente,
	"/api/naves/${casoSubsanar.naveId}/tramite/estado",
	[accion: 'SUBSANAR']
)

WS.verifyResponseStatusCode(subsanacion, 200)

KeywordUtil.logInfo('El agente ejecutó SUBSANAR correctamente.')

// No debe aparecer un correo nuevo.
verificarSinCorreoNuevo(
	casoSubsanar.nombre,
	casoSubsanar.mensajesBase
)

// =====================================================
// 3. SEGUNDO EXPEDIENTE: DESISTIR.
// =====================================================

def casoDesistir = crearExpedienteObservado(
	'HU16 Desistir',
	agente,
	dgmm
)

KeywordUtil.logInfo(
	"Expediente para DESISTIR: ${casoDesistir.nombre}"
)

// El agente desiste.
def desistimiento = H.pedir(
	agente,
	"/api/naves/${casoDesistir.naveId}/tramite/estado",
	[accion: 'DESISTIR']
)

WS.verifyResponseStatusCode(desistimiento, 200)

KeywordUtil.logInfo('El agente ejecutó DESISTIR correctamente.')

// Tampoco debe aparecer un correo nuevo.
verificarSinCorreoNuevo(
	casoDesistir.nombre,
	casoDesistir.mensajesBase
)

// =====================================================
// 4. Resultado.
// =====================================================

KeywordUtil.markPassed(
	'HU16_07: Las acciones SUBSANAR y DESISTIR del agente se ejecutaron correctamente sin generar notificaciones adicionales. Se verificaron ambos expedientes en Mailpit.'
)