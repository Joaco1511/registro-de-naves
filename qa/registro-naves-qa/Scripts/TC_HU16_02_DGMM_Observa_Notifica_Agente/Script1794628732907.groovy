import navesitas.Hu13 as H
import navesitas.Http as Http

import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.util.KeywordUtil

import java.net.URLEncoder

// =====================================================
// HU16_02 - DGMM observa el tramite y notifica al agente.
// =====================================================

String correoAgente = 'agente@navesitas.pa'
String sufijo = System.currentTimeMillis().toString()
String nombreNave = 'HU16 Observar ' + sufijo

String observacion = 'Falta corregir la informacion del certificado de propiedad.'

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

// =====================================================
// 1. Iniciar sesiones.
// =====================================================

String agente = H.login(correoAgente)
String dgmm = H.login('revisor@navesitas.pa')

assert agente != null : 'No se pudo iniciar sesión como agente.'
assert dgmm != null : 'No se pudo iniciar sesión como DGMM.'

// =====================================================
// 2. Crear nave y preparar expediente.
// =====================================================

def naveId = H.nave(agente, [
	nombre: nombreNave,
	eslora: 24
])

assert naveId != null : 'No se pudo crear la nave.'

H.preparar(agente, naveId)

KeywordUtil.logInfo("Nave creada: ${nombreNave}, ID: ${naveId}")

// =====================================================
// 3. Cargar documentos.
// =====================================================

[
	'CERTIFICADO_PROPIEDAD',
	'PODER_NOTARIAL',
	'ITC'
].each { tipo ->
	H.cargar(agente, naveId, tipo)
}

// =====================================================
// 4. DGMM admite el trámite.
// =====================================================

def admision = H.accion(dgmm, naveId, 'ADMITIR')

WS.verifyResponseStatusCode(admision, 200)

// =====================================================
// 5. DGMM observa el trámite.
// =====================================================

def observacionTramite = H.pedir(
	dgmm,
	"/api/naves/${naveId}/tramite/estado",
	[
		accion: 'OBSERVAR',
		observacion: observacion
	]
)

WS.verifyResponseStatusCode(observacionTramite, 200)

KeywordUtil.logInfo('DGMM observó correctamente el trámite.')

// =====================================================
// 6. Buscar el correo correspondiente a la observación.
// =====================================================

def aviso = null

for (int intento = 0; intento < 12 && aviso == null; intento++) {

	def mensajes = buscarMensajes(correoAgente)

	for (def resumen : mensajes) {

		def detalle = leerMensaje(resumen.ID)
		String texto = detalle.Text ?: ''

		if (texto.contains(nombreNave) &&
			texto.contains('Acción: Observar trámite')) {

			aviso = detalle
			break
		}
	}

	if (aviso == null) {
		Thread.sleep(500)
	}
}

// =====================================================
// 7. Verificar el correo.
// =====================================================

assert aviso != null :
	'No llegó a Mailpit el aviso de observación del trámite.'

assert aviso.Subject ==
	'Registro de Naves: actualización de su trámite' :
	"Asunto incorrecto: ${aviso.Subject}"

String contenido = aviso.Text

assert contenido.contains("Nave: ${nombreNave}") :
	'El correo no contiene el nombre de la nave.'

assert contenido.contains("Número de registro: ${naveId}") :
	'El correo no contiene el número de registro.'

assert contenido.contains('Acción: Observar trámite') :
	'El correo no identifica la acción de observación.'

assert contenido.contains("Observación: ${observacion}") :
	'El correo no contiene la observación registrada por DGMM.'

assert contenido.contains(
	"/expediente.html?naveId=${naveId}"
) :
	'El correo no contiene el enlace correcto al expediente.'

// =====================================================
// 8. Resultado.
// =====================================================

KeywordUtil.logInfo('Asunto: ' + aviso.Subject)
KeywordUtil.logInfo('Observación verificada: ' + observacion)
KeywordUtil.logInfo('Correo recibido correctamente en Mailpit.')

KeywordUtil.markPassed(
	'HU16_02: La observación del trámite generó correctamente un correo al agente. Se verificaron asunto, nave, número de registro, acción, observación y enlace.'
)