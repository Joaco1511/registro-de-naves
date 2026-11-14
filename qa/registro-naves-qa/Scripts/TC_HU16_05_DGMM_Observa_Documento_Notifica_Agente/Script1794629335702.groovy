import navesitas.Hu13 as H
import navesitas.Http as Http

import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.util.KeywordUtil

import java.net.URLEncoder

// =====================================================
// HU16_05 - DGMM observa un documento y notifica al agente.
// =====================================================

String correoAgente = 'agente@navesitas.pa'
String sufijo = System.currentTimeMillis().toString()
String nombreNave = 'HU16 Observar Documento ' + sufijo

String observacion = 'El certificado de propiedad debe corregirse y presentarse nuevamente.'

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
// 3. Cargar documentos requeridos.
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

KeywordUtil.logInfo('DGMM admitió correctamente el trámite.')

// =====================================================
// 5. Consultar los documentos y localizar el certificado.
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
	'No se encontró el documento CERTIFICADO_PROPIEDAD.'

assert documento.version != null :
	'El documento no contiene información de su versión.'

KeywordUtil.logInfo(
	"Documento seleccionado: ${documento.tipo}, versión ${documento.version}"
)

// =====================================================
// 6. DGMM observa el documento.
// =====================================================

def revision = H.pedir(
	dgmm,
	"/api/naves/${naveId}/documentos/${documento.id}/revision",
	[
		decision: 'OBSERVAR',
		observacion: observacion
	]
)

WS.verifyResponseStatusCode(revision, 200)

KeywordUtil.logInfo('DGMM observó correctamente el documento.')

// =====================================================
// 7. Buscar el correo de observación del documento.
// =====================================================

def aviso = null

for (int intento = 0; intento < 12 && aviso == null; intento++) {

	def mensajes = buscarMensajes(correoAgente)

	for (def resumen : mensajes) {

		def detalle = leerMensaje(resumen.ID)
		String texto = detalle.Text ?: ''

		if (texto.contains(nombreNave) &&
			texto.contains('Acción: Observar documento')) {

			aviso = detalle
			break
		}
	}

	if (aviso == null) {
		Thread.sleep(500)
	}
}

// =====================================================
// 8. Verificar el correo recibido.
// =====================================================

assert aviso != null :
	'No llegó a Mailpit el aviso de observación del documento.'

assert aviso.Subject ==
	'Registro de Naves: actualización de su trámite' :
	"Asunto incorrecto: ${aviso.Subject}"

String contenido = aviso.Text

assert contenido.contains("Nave: ${nombreNave}") :
	'El correo no contiene el nombre de la nave.'

assert contenido.contains("Número de registro: ${naveId}") :
	'El correo no contiene el número de registro.'

assert contenido.contains('Acción: Observar documento') :
	'El correo no identifica la observación del documento.'

assert contenido.contains(
	"Documento: CERTIFICADO_PROPIEDAD (versión ${documento.version})"
) :
	'El correo no identifica correctamente el documento y su versión.'

assert contenido.contains("Observación: ${observacion}") :
	'El correo no contiene la observación realizada por DGMM.'

assert contenido.contains(
	"/expediente.html?naveId=${naveId}"
) :
	'El correo no contiene el enlace correcto al expediente.'

// =====================================================
// 9. Resultado.
// =====================================================

KeywordUtil.logInfo('Asunto: ' + aviso.Subject)
KeywordUtil.logInfo('Documento y versión verificados.')
KeywordUtil.logInfo('Observación verificada: ' + observacion)
KeywordUtil.logInfo('Destinatario: ' + correoAgente)

KeywordUtil.markPassed(
	'HU16_05: La observación de un documento generó correctamente un correo al agente. Se verificaron asunto, nave, número de registro, acción, documento, versión, observación y enlace.'
)