
import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import navesitas.Http
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URLEncoder

String base = 'http://localhost:8080'

// Credenciales de la cuenta administrativa de desarrollo.
// Sustituya el valor por la contraseña de demostración.
// No publique ni comparta este dato.
String adminCorreo = 'admin@navesitas.pa'
String adminPassword = 'Navesitas2026*'

// Correo único para cada ejecución
String correoNuevo = 'hu14.nuevo.dgmm.' +
	System.currentTimeMillis() + '@navesitas.pa'

// 1. Iniciar sesión como administrador
RequestObject login = new RequestObject('loginAdmin')

login.setRestUrl(base + '/api/auth/login')
login.setRestRequestMethod('POST')

login.setHttpHeaderProperties([
	new TestObjectProperty(
		'Content-Type',
		ConditionType.EQUALS,
		'application/x-www-form-urlencoded'
	)
])

String datosLogin =
	'correo=' + URLEncoder.encode(adminCorreo, 'UTF-8') +
	'&password=' + URLEncoder.encode(adminPassword, 'UTF-8')

login.setBodyContent(new HttpTextBodyContent(datosLogin))

ResponseObject loginResponse = Http.enviar(login)

println('Login administrador: ' + loginResponse.getStatusCode())

String cookie = Http.sesion(loginResponse)

assert cookie != null :
	'No se pudo obtener la sesión del administrador.'

// 2. Preparar el usuario DGMM
Map cuerpo = [
	correo: correoNuevo,
	password: 'PasswordPrueba2026',
	nombreCompleto: 'Funcionario de Prueba Katalon',
	rol: 'FUNCIONARIO_DGMM'
]

// 3. Crear la solicitud administrativa
RequestObject alta = new RequestObject('crearUsuarioDGMM')

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

// Http.enviar agrega automáticamente el token CSRF
ResponseObject r = Http.enviar(alta)

println('Correo creado: ' + correoNuevo)
println('Status: ' + r.getStatusCode())
println('Respuesta: ' + r.getResponseBodyContent())

// 4. Validar la creación
WS.verifyResponseStatusCode(r, 201)

def respuesta = new JsonSlurper().parseText(
	r.getResponseBodyContent()
)

assert respuesta.correo == correoNuevo
assert respuesta.nombreCompleto == 'Funcionario de Prueba Katalon'
assert respuesta.rol == 'FUNCIONARIO_DGMM'
assert respuesta.correoValidado == false

// 5. Comprobar que no se expongan datos sensibles
assert !respuesta.containsKey('password')
assert !respuesta.containsKey('token')
assert !respuesta.containsKey('passwordHash')

assert respuesta.mensaje.contains('validar su correo')

println('TC_HU14_04 completado correctamente.')


