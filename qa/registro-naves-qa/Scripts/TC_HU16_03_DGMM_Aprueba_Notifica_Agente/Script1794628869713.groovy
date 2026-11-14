import navesitas.Hu13 as H
import navesitas.Http as Http

import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.util.KeywordUtil

import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

// =====================================================
// HU16_03 - DGMM aprueba el tramite y notifica al agente.
// =====================================================

String correoAgente = 'agente@navesitas.pa'
String sufijo = System.currentTimeMillis().toString()
String nombreNave = 'HU16 Aprobar ' + sufijo

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
// 4. DGMM admite y observa el trámite.
// =====================================================

WS.verifyResponseStatusCode(
	H.accion(dgmm, naveId, 'ADMITIR'), 200
)

WS.verifyResponseStatusCode(
	H.accion(dgmm, naveId, 'OBSERVAR'), 200
)

// El agente subsana las observaciones.
WS.verifyResponseStatusCode(
	H.accion(agente, naveId, 'SUBSANAR'), 200
)

// =====================================================
// 5. DGMM aprueba todos los documentos.
// =====================================================

def documentos = H.json(
	H.pedir(dgmm, "/api/naves/${naveId}/documentos")
)

assert documentos.size() == 3 :
	"Se esperaban 3 documentos, se encontraron ${documentos.size()}."

documentos.each { documento ->

	def revision = H.pedir(
		dgmm,
		"/api/naves/${naveId}/documentos/${documento.id}/revision",
		[decision: 'APROBAR']
	)

	WS.verifyResponseStatusCode(revision, 200)
}

KeywordUtil.logInfo('Los tres documentos fueron aprobados.')

// =====================================================
// 6. DGMM aprueba definitivamente el trámite.
// =====================================================

// Se utiliza H.pedir directamente para no enviar una
// observación adicional en la acción de aprobación.

def aprobacion = H.pedir(
	dgmm,
	"/api/naves/${naveId}/tramite/estado",
	[accion: 'APROBAR']
)

WS.verifyResponseStatusCode(aprobacion, 200)

KeywordUtil.logInfo('DGMM aprobó correctamente el trámite.')

// =====================================================
// 7. Buscar el aviso de aprobación en Mailpit.
// =====================================================

def aviso = null

for (int intento = 0; intento < 12 && aviso == null; intento++) {

	def mensajes = buscarMensajes(correoAgente)

	for (def resumen : mensajes) {

		def detalle = leerMensaje(resumen.ID)
		String texto = detalle.Text ?: ''

		if (texto.contains(nombreNave) &&
			texto.contains('Acción: Aprobar trámite')) {

			aviso = detalle
			break
		}
	}

	if (aviso == null) {
		Thread.sleep(500)
	}
}

// =====================================================
// 8. Verificar el correo.
// =====================================================

assert aviso != null :
	'No llegó a Mailpit el aviso de aprobación del trámite.'

assert aviso.Subject ==
	'Registro de Naves: actualización de su trámite' :
	"Asunto incorrecto: ${aviso.Subject}"

String contenido = aviso.Text

assert contenido.contains("Nave: ${nombreNave}") :
	'El correo no contiene el nombre de la nave.'

assert contenido.contains("Número de registro: ${naveId}") :
	'El correo no contiene el número de registro.'

assert contenido.contains('Acción: Aprobar trámite') :
	'El correo no identifica la acción de aprobación.'

// Comprobar que el detalle identifica el estado aprobado.
String contenidoNormalizado = Normalizer.normalize(
	contenido,
	Normalizer.Form.NFD
).replaceAll('\\p{M}+', '').toLowerCase(Locale.ROOT)

assert contenidoNormalizado.contains('estado del tramite: aprobada') :
	'El correo no indica que el trámite quedó aprobado.'

assert contenido.contains(
	"/expediente.html?naveId=${naveId}"
) :
	'El correo no contiene el enlace correcto al expediente.'

// =====================================================
// 9. Resultado.
// =====================================================

KeywordUtil.logInfo('Asunto: ' + aviso.Subject)
KeywordUtil.logInfo('Acción de aprobación verificada.')
KeywordUtil.logInfo('Destinatario: ' + correoAgente)

KeywordUtil.markPassed(
	'HU16_03: La aprobación del trámite generó correctamente un correo al agente. Se verificaron asunto, nave, número de registro, acción, estado aprobado y enlace.'
)