import navesitas.Hu13 as H
import navesitas.Http as Http

import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.util.KeywordUtil

import java.net.URLEncoder

// =====================================================
// HU16_01 - Al admitir el tramite, DGMM notifica
// por correo al agente que registro la nave.
// =====================================================

String correoAgente = 'agente@navesitas.pa'

String nombreNave = 'HU16 Admitir ' + System.currentTimeMillis()

// -----------------------------------------------------
// Funcion auxiliar: buscar mensajes por destinatario.
// -----------------------------------------------------

def buscarMensajes = { String correo ->

	RequestObject busqueda = new RequestObject('MailpitBuscar')

	String consulta = URLEncoder.encode(
		'to:' + correo,
		'UTF-8'
	)

	busqueda.setRestUrl(
		'http://localhost:8025/api/v1/search?query=' + consulta
	)

	busqueda.setRestRequestMethod('GET')

	def respuesta = Http.enviar(busqueda)

	WS.verifyResponseStatusCode(respuesta, 200)

	return H.json(respuesta).messages ?: []
}

// -----------------------------------------------------
// Funcion auxiliar: consultar el contenido de un correo.
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
// 1. Iniciar sesión como agente y funcionario DGMM.
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
// 3. Cargar los documentos requeridos.
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
// 5. Esperar y buscar el correo enviado a agente@navesitas.pa.
// =====================================================

def aviso = null

for (int intento = 0; intento < 12 && aviso == null; intento++) {

	def mensajes = buscarMensajes(correoAgente)

	for (def resumen : mensajes) {

		def detalle = leerMensaje(resumen.ID)

		String texto = detalle.Text ?: ''

		if (texto.contains(nombreNave) &&
			texto.contains('Acción: Admitir trámite')) {

			aviso = detalle
			break
		}
	}

	if (aviso == null) {
		Thread.sleep(500)
	}
}

// =====================================================
// 6. Verificar que el correo llegó.
// =====================================================

assert aviso != null :
	'No llegó a Mailpit el aviso de admisión para el agente.'

// Verificar el asunto.
assert aviso.Subject ==
	'Registro de Naves: actualización de su trámite' :
	"El asunto del correo es incorrecto: ${aviso.Subject}"

// Verificar contenido.
String contenido = aviso.Text

assert contenido.contains("Nave: ${nombreNave}") :
	'El correo no contiene el nombre de la nave.'

assert contenido.contains("Número de registro: ${naveId}") :
	'El correo no contiene el número de registro.'

assert contenido.contains('Acción: Admitir trámite') :
	'El correo no identifica la acción de admisión.'

assert contenido.contains(
	"/expediente.html?naveId=${naveId}"
) :
	'El correo no contiene el enlace correcto al expediente.'

// =====================================================
// 7. Resultado.
// =====================================================

KeywordUtil.logInfo('Asunto: ' + aviso.Subject)
KeywordUtil.logInfo('Destinatario consultado: ' + correoAgente)
KeywordUtil.logInfo('Contenido del correo verificado correctamente.')

KeywordUtil.markPassed(
	'HU16_01: La admisión del trámite generó correctamente un correo al agente registrado. Se verificaron asunto, nave, número de registro, acción y enlace al expediente.'
)