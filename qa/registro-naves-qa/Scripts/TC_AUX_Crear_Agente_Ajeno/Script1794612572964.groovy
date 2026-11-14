import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import navesitas.Http

String base = 'http://localhost:8080'

// Generar un correo único para evitar conflictos.
String correo = 'agente.ajeno.' + System.currentTimeMillis() + '@navesitas.pa'

String clave = System.getenv('NAVESITAS_CLAVE') ?: 'Navesitas2026*'

String cuerpo = """{
    "correo": "${correo}",
    "password": "${clave}",
    "nombreCompleto": "Agente de Prueba Externo",
    "rol": "AGENTE_NAVIERO"
}"""

RequestObject registro = new RequestObject('registroAgenteAjeno')
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

def respuesta = Http.enviar(registro)

println("Correo creado: ${correo}")
println("Código HTTP: ${respuesta.getStatusCode()}")

assert respuesta.getStatusCode() == 201 :
	"Se esperaba HTTP 201, obtenido: ${respuesta.getStatusCode()}"

assert respuesta.getResponseBodyContent().contains('AGENTE_NAVIERO') :
	'La cuenta no fue creada con el rol esperado.'

println('Cuenta de agente ajeno creada correctamente.')