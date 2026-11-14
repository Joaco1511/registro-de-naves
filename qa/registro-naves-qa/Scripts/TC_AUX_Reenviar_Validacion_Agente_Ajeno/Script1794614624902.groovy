import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import navesitas.Http

String correo = 'agente.ajeno.1794612612756@navesitas.pa'

RequestObject peticion = new RequestObject('reenviarValidacion')

peticion.setRestUrl(
	'http://localhost:8080/api/auth/reenviar-validacion'
)

peticion.setRestRequestMethod('POST')

peticion.setHttpHeaderProperties([
	new TestObjectProperty(
		'Content-Type',
		ConditionType.EQUALS,
		'application/json'
	)
])

peticion.setBodyContent(
	new HttpTextBodyContent("""{"correo":"${correo}"}""")
)

def respuesta = Http.enviar(peticion)

println("HTTP obtenido: ${respuesta.getStatusCode()}")

assert respuesta.getStatusCode() == 202 :
    'El endpoint no aceptó el reenvío.'

println('Solicitud de reenvío aceptada.')