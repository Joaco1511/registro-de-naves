import navesitas.Hu13 as H
import navesitas.Http as Http

import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.util.KeywordUtil

import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// =====================================================
// HU16_08 - El fallo SMTP no debe revertir la operacion.
// =====================================================

String correoAgente = 'agente@navesitas.pa'
String sufijo = System.currentTimeMillis().toString()
String nombreNave = 'HU16 Fallo SMTP ' + sufijo

boolean contenedorDetenido = false

// -----------------------------------------------------
// Ejecutar comandos Docker desde Katalon.
// -----------------------------------------------------

def ejecutarDocker = { List<String> argumentos ->

	List<String> comando = ['docker'] + argumentos

	KeywordUtil.logInfo(
		'Ejecutando: ' + comando.join(' ')
	)

	Process proceso = new ProcessBuilder(comando)
		.redirectErrorStream(true)
		.start()

	boolean termino = proceso.waitFor(45, TimeUnit.SECONDS)

	if (!termino) {
		proceso.destroyForcibly()

		throw new AssertionError(
			'El comando Docker excedió los 45 segundos: ' +
			comando.join(' ')
		)
	}

	String salida = proceso.inputStream.getText('UTF-8').trim()

	return [
		codigo: proceso.exitValue(),
		salida: salida
	]
}

// -----------------------------------------------------
// Consultar mensajes de Mailpit.
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
// Leer un mensaje.
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
// Buscar mensajes que mencionen una nave específica.
// -----------------------------------------------------

def mensajesPorNave = { String nombre ->

	Map encontrados = [:]

	buscarMensajes(correoAgente).each { resumen ->

		def detalle = leerMensaje(resumen.ID)

		String texto = detalle.Text ?: ''

		if (texto.contains(nombre)) {
			encontrados[resumen.ID.toString()] = detalle
		}
	}

	return encontrados
}

// =====================================================
// 1. Iniciar sesiones.
// =====================================================

String agente = H.login(correoAgente)
String dgmm = H.login('revisor@navesitas.pa')

assert agente != null :
	'No se pudo iniciar sesión como agente.'

assert dgmm != null :
	'No se pudo iniciar sesión como DGMM.'

// =====================================================
// 2. Crear nave y preparar expediente.
// =====================================================

def naveId = H.nave(agente, [
	nombre: nombreNave,
	eslora: 24
])

assert naveId != null :
	'No se pudo crear la nave.'

H.preparar(agente, naveId)

KeywordUtil.logInfo(
	"Nave creada: ${nombreNave}, ID: ${naveId}"
)

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
// 4. Consultar estado inicial.
// =====================================================

def respuestaInicial = H.pedir(
	dgmm,
	"/api/naves/${naveId}/tramite"
)

WS.verifyResponseStatusCode(respuestaInicial, 200)

def estadoInicial = H.json(respuestaInicial)

assert estadoInicial.estado != null :
	'No se pudo obtener el estado inicial del trámite.'

KeywordUtil.logInfo(
	"Estado inicial: ${estadoInicial.estado}"
)

// =====================================================
// 5. Confirmar que el correo no tiene avisos previos
//    asociados a esta nave.
// =====================================================

def mensajesIniciales = mensajesPorNave(nombreNave)

assert mensajesIniciales.isEmpty() :
	'Ya existen correos relacionados con esta nave antes de la prueba.'

// =====================================================
// 6. Comprobar que Mailpit está funcionando.
// =====================================================

def inspeccionInicial = ejecutarDocker([
	'inspect',
	'--format={{.State.Running}}',
	'amp-correo'
])

assert inspeccionInicial.codigo == 0 :
	'No se pudo consultar el contenedor amp-correo.'

assert inspeccionInicial.salida == 'true' :
	'Mailpit debe estar activo antes de comenzar la prueba.'

KeywordUtil.logInfo(
	'Mailpit está activo antes de la prueba.'
)

// =====================================================
// 7. Detener Mailpit y ejecutar la operación.
// =====================================================

try {

	def detencion = ejecutarDocker([
		'stop',
		'amp-correo'
	])

	assert detencion.codigo == 0 :
		'No se pudo detener el contenedor amp-correo.'

	contenedorDetenido = true

	// Confirmar que realmente está detenido.
	def estadoContenedor = ejecutarDocker([
		'inspect',
		'--format={{.State.Running}}',
		'amp-correo'
	])

	assert estadoContenedor.codigo == 0 :
		'No se pudo comprobar el estado de Mailpit.'

	assert estadoContenedor.salida == 'false' :
		'Mailpit todavía aparece como activo.'

	KeywordUtil.logInfo(
		'Mailpit está detenido. Se probará el envío SMTP.'
	)

	// -------------------------------------------------
	// 8. DGMM admite el trámite con Mailpit detenido.
	// -------------------------------------------------

	def admision = H.accion(
		dgmm,
		naveId,
		'ADMITIR'
	)

	// La aplicación debe responder correctamente
	// aunque el envío del correo falle.
	WS.verifyResponseStatusCode(admision, 200)

	KeywordUtil.logInfo(
		'La operación ADMITIR respondió HTTP 200.'
	)

	// -------------------------------------------------
	// 9. Consultar el estado después de la operación.
	// -------------------------------------------------

	def respuestaFinal = H.pedir(
		dgmm,
		"/api/naves/${naveId}/tramite"
	)

	WS.verifyResponseStatusCode(respuestaFinal, 200)

	def estadoFinal = H.json(respuestaFinal)

	assert estadoFinal.estado != null :
		'No se pudo consultar el estado final.'

	assert estadoFinal.estado.toString() !=
		   estadoInicial.estado.toString() :
		'El estado no cambió después de ejecutar ADMITIR.'

	assert estadoFinal.esFinal == false :
		'El trámite no debería encontrarse en estado final.'

	KeywordUtil.logInfo(
		"Estado inicial: ${estadoInicial.estado}"
	)

	KeywordUtil.logInfo(
		"Estado final: ${estadoFinal.estado}"
	)

	// -------------------------------------------------
	// 10. Comprobar el historial persistido.
	// -------------------------------------------------

	def respuestaHistorial = H.pedir(
		dgmm,
		"/api/naves/${naveId}/tramite/historial"
	)

	WS.verifyResponseStatusCode(respuestaHistorial, 200)

	def historial = H.json(respuestaHistorial)

	def entrada = historial.find {
		it.accion?.toString() == 'ADMITIR'
	}

	assert entrada != null :
		'No se encontró la acción ADMITIR en el historial.'

	assert entrada.estadoNuevo?.toString() ==
		   estadoFinal.estado.toString() :
		'El estado del historial no coincide con el estado actual.'

	assert entrada.rol == 'FUNCIONARIO_DGMM' :
		'El historial no identifica correctamente al funcionario DGMM.'

	KeywordUtil.logInfo(
		'La admisión quedó guardada en el historial.'
	)

} finally {

	// =================================================
	// 11. Restaurar Mailpit incluso si falla una prueba.
	// =================================================

	if (contenedorDetenido) {

		KeywordUtil.logInfo(
			'Restaurando el contenedor amp-correo...'
		)

		def inicio = ejecutarDocker([
			'start',
			'amp-correo'
		])

		assert inicio.codigo == 0 :
			'No se pudo reiniciar Mailpit. Debe iniciarse manualmente.'

		boolean restaurado = false

		for (int intento = 0; intento < 12; intento++) {

			def estado = ejecutarDocker([
				'inspect',
				'--format={{.State.Running}}',
				'amp-correo'
			])

			if (estado.codigo == 0 &&
				estado.salida == 'true') {

				restaurado = true
				break
			}

			Thread.sleep(500)
		}

		assert restaurado :
			'Mailpit no volvió a quedar activo.'

		KeywordUtil.logInfo(
			'Mailpit fue reiniciado correctamente.'
		)
	}
}

// =====================================================
// 12. Confirmar que Mailpit volvió a responder.
// =====================================================

boolean mailpitListo = false

for (int intento = 0; intento < 12; intento++) {

	try {

		buscarMensajes(correoAgente)

		mailpitListo = true
		break

	} catch (Exception e) {

		Thread.sleep(500)
	}
}

assert mailpitListo :
	'Mailpit no respondió después de reiniciarse.'

// =====================================================
// 13. Confirmar que no se entregó el correo de admisión.
// =====================================================

def mensajesFinales = mensajesPorNave(nombreNave)

assert mensajesFinales.isEmpty() :
	'Se encontró un correo inesperado para esta nave.'

// =====================================================
// 14. Resultado.
// =====================================================

KeywordUtil.logInfo(
	'La operación quedó persistida aunque SMTP estaba fuera de servicio.'
)

KeywordUtil.logInfo(
	'Mailpit está nuevamente activo.'
)

KeywordUtil.markPassed(
	'HU16_08: El fallo SMTP no revirtió la admisión del trámite. Se verificaron HTTP 200, cambio de estado, historial persistido, ausencia de correo entregado y recuperación de Mailpit.'
)