import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import navesitas.Http
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import groovy.json.JsonSlurper

String base = 'http://localhost:8080'

// Correo unico para evitar conflictos con registros anteriores
String correo = 'hu14.auditor.' +
	System.currentTimeMillis() + '@navesitas.pa'

// Solicitamos un rol privilegiado desde el registro publico
String cuerpo = """{
    "correo": "${correo}",
    "password": "Navesitas2026*",
    "nombreCompleto": "Prueba Katalon HU14 AUD",
    "rol": "AUDITOR"
}"""

RequestObject registro = new RequestObject('registroHU14_AUD')

registro.setRestUrl(base + '/api/auth/registro')
registro.setRestRequestMethod('POST')

registro.setHttpHeaderProperties([
	new TestObjectProperty(
		'Content-Type',
		ConditionType.EQUALS,
		'application/json'
	)
])

registro.setBodyContent(new HttpTextBodyContent(cuerpo))

ResponseObject r = Http.enviar(registro)

println('Correo utilizado: ' + correo)
println('Status: ' + r.getStatusCode())
println('Respuesta: ' + r.getResponseBodyContent())

// Verificar codigo HTTP esperado
WS.verifyResponseStatusCode(r, 400)

// Convertir respuesta a JSON
def respuesta = new JsonSlurper().parseText(
	r.getResponseBodyContent()
)

// Verificar mensaje general
assert respuesta.mensaje ==
	'Hay campos obligatorios sin completar o con formato inválido.'

// Verificar rechazo especifico del rol
assert respuesta.campos.rol ==
	'Ese rol no se puede elegir al crear una cuenta.'
