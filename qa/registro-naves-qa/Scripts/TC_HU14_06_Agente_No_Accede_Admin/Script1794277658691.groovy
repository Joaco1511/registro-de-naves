
import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS

import navesitas.Http
import groovy.json.JsonOutput
import java.net.URLEncoder

// ==================================================
// CONFIGURACION
// ==================================================

String base = 'http://localhost:8080'

String correoActor = 'agente@navesitas.pa'

String correoNuevo = 'hu14.bloqueo.agente.' +
	System.currentTimeMillis() + '@navesitas.pa'


// ==================================================
// 1. INICIAR SESION
// Utilizamos exactamente el metodo del caso auxiliar
// ==================================================

def iniciarSesion = { String correo ->

	RequestObject login = new RequestObject('login')

	login.setRestUrl(base + '/api/auth/login')
	login.setRestRequestMethod('POST')

	login.setHttpHeaderProperties([
		new TestObjectProperty(
			'Content-Type',
			ConditionType.EQUALS,
			'application/x-www-form-urlencoded'
		)
	])

	login.setBodyContent(new HttpTextBodyContent(
		'correo=' + correo + '&password=Navesitas2026*'
	))

	ResponseObject respuestaLogin = Http.enviar(login)

	println('======================================')
	println('DIAGNOSTICO DEL LOGIN')
	println('======================================')

	println('Login status: ' + respuestaLogin.getStatusCode())

	println('Login Location: ' +
		respuestaLogin.getHeaderField('Location'))

	println('Cabeceras recibidas: ' +
		respuestaLogin.getHeaderFields()?.keySet())

	// Obtener la sesion mediante la keyword original
	String sesion = Http.sesion(respuestaLogin)

	println('Sesion obtenida: ' + (sesion != null))

	return sesion
}


// ==================================================
// 2. AUTENTICACION COMO AGENTE
// ==================================================

String cookie = iniciarSesion(correoActor)

assert cookie != null :
	'No se pudo iniciar sesion como agente naviero.'


// ==================================================
// 3. COMPROBAR QUE LA SESION ES VALIDA
// ==================================================

RequestObject validarSesion = new RequestObject('validarSesion')

validarSesion.setRestUrl(base + '/api/auth/sesion')
validarSesion.setRestRequestMethod('GET')

validarSesion.setHttpHeaderProperties([
	new TestObjectProperty(
		'Cookie',
		ConditionType.EQUALS,
		cookie
	)
])

ResponseObject sesionResponse = Http.enviar(validarSesion)

println('======================================')
println('VALIDACION DE SESION')
println('======================================')

println('Estado de sesion: ' + sesionResponse.getStatusCode())

println('Respuesta de sesion: ' +
	sesionResponse.getResponseBodyContent())

WS.verifyResponseStatusCode(sesionResponse, 200)


// ==================================================
// 4. PREPARAR SOLICITUD ADMINISTRATIVA
// ==================================================

Map cuerpo = [
	correo: correoNuevo,
	password: 'PasswordPrueba2026',
	nombreCompleto: 'Intento de acceso no autorizado',
	rol: 'FUNCIONARIO_DGMM'
]

RequestObject alta = new RequestObject('intentoAdminAgente')

alta.setRestUrl(base + '/api/admin/usuarios')
alta.setRestRequestMethod('POST')

alta.setHttpHeaderProperties([
	new TestObjectProperty(
		'Content-Type',
		ConditionType.EQUALS,
		'application/json'
	),
	new TestObjectProperty(
		'Cookie',
		ConditionType.EQUALS,
		cookie
	)
])

alta.setBodyContent(
	new HttpTextBodyContent(JsonOutput.toJson(cuerpo))
)


// ==================================================
// 5. EJECUTAR INTENTO DE ACCESO
// ==================================================

ResponseObject r = Http.enviar(alta)

println('======================================')
println('RESULTADO DE ACCESO ADMINISTRATIVO')
println('======================================')

println('Usuario: ' + correoActor)

println('Status: ' + r.getStatusCode())

println('Respuesta: ' + r.getResponseBodyContent())


// ==================================================
// 6. VERIFICAR RESTRICCION DE PERMISOS
// ==================================================

WS.verifyResponseStatusCode(r, 403)

println('TC_HU14_06 completado correctamente.')
