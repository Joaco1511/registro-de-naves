import navesitas.Hu13 as H
import navesitas.Http as Http

import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.util.KeywordUtil

import java.net.URLEncoder

// =====================================================
// HU16_06 - Aprobar un documento NO debe generar correo.
// =====================================================

String correoAgente = 'agente@navesitas.pa'
String sufijo = System.currentTimeMillis().toString()
String nombreNave = 'HU16 Aprobar Documento ' + sufijo

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
// Leer el contenido de un mensaje.
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
// Obtener IDs de correos relacionados con esta nave.
// -----------------------------------------------------

def obtenerIdsAvisos = {

	Set<String> ids = [] as Set

	def mensajes = buscarMensajes(correoAgente)

	mensajes.each { resumen ->

		def detalle = leerMensaje(resumen.ID)
		String texto = detalle.Text ?: ''

		if (texto.contains(nombreNave)) {
			ids.add(resumen.ID.toString())
		}
	}

	return ids
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
// Esta acción SÍ debe generar un correo.
// =====================================================

def admision = H.accion(dgmm, naveId, 'ADMITIR')

WS.verifyResponseStatusCode(admision, 200)

// Esperar a que llegue el correo de admisión.
Set<String> idsAntes = [] as Set

for (int intento = 0; intento < 12 && idsAntes.isEmpty(); intento++) {

	idsAntes = obtenerIdsAvisos()

	if (idsAntes.isEmpty()) {
		Thread.sleep(500)
	}
}

assert !idsAntes.isEmpty() :
	'No llegó el correo de admisión que debe servir como referencia.'

// Verificar que el correo inicial corresponde a ADMITIR.
boolean avisoAdmisionEncontrado = false

idsAntes.each { id ->

	def mensaje = leerMensaje(id)
	String texto = mensaje.Text ?: ''

	if (texto.contains('Acción: Admitir trámite')) {
		avisoAdmisionEncontrado = true
	}
}

assert avisoAdmisionEncontrado :
	'No se encontró el correo de admisión esperado.'

KeywordUtil.logInfo(
	"Correo de admisión recibido. Mensajes iniciales: ${idsAntes.size()}"
)

// =====================================================
// 5. Consultar documentos.
// =====================================================

def documentos = H.json(
	H.pedir(dgmm, "/api/naves/${naveId}/documentos")
)

assert documentos.size() == 3 :
	"Se esperaban 3 documentos y se encontraron ${documentos.size()}."

def documento = documentos.find {
	it.tipo == 'CERTIFICADO_PROPIEDAD'
}

assert documento != null :
	'No se encontró CERTIFICADO_PROPIEDAD.'

// =====================================================
// 6. DGMM aprueba el documento.
// Esta acción NO debe generar correo.
// =====================================================

def revision = H.pedir(
	dgmm,
	"/api/naves/${naveId}/documentos/${documento.id}/revision",
	[decision: 'APROBAR']
)

WS.verifyResponseStatusCode(revision, 200)

KeywordUtil.logInfo(
	'DGMM aprobó el documento correctamente.'
)

// =====================================================
// 7. Esperar y comprobar que no apareció otro correo.
// =====================================================

Set<String> idsDespues = [] as Set

for (int intento = 0; intento < 12; intento++) {

	idsDespues = obtenerIdsAvisos()

	Set<String> nuevos = idsDespues - idsAntes

	if (!nuevos.isEmpty()) {
		break
	}

	Thread.sleep(500)
}

// Ningún correo nuevo debe estar relacionado con esta nave.
Set<String> nuevos = idsDespues - idsAntes

assert nuevos.isEmpty() :
	"Se recibió un correo inesperado después de aprobar el documento. IDs: ${nuevos}"

assert idsDespues == idsAntes :
	'La cantidad de notificaciones relacionadas con la nave cambió.'

// =====================================================
// 8. Resultado.
// =====================================================

KeywordUtil.logInfo(
	'La aprobación del documento no generó notificaciones adicionales.'
)

KeywordUtil.logInfo(
	"Correos relacionados con la nave antes y después: ${idsDespues.size()}"
)

KeywordUtil.markPassed(
	'HU16_06: Aprobar un documento no generó una notificación por correo. Se confirmó que el correo previo de admisión permaneció como único aviso de la nave.'
)