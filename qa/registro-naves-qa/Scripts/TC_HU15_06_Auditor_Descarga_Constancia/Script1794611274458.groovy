import navesitas.Hu13 as H
import com.kms.katalon.core.util.KeywordUtil

Long naveId = 218L

// 1. Iniciar sesión como auditor.
def cookie = H.login('auditor@navesitas.pa')

assert cookie != null :
	'No se pudo iniciar sesión como auditor.'

// 2. Descargar la constancia.
def respuesta = H.pedir(
	cookie,
	"/api/naves/${naveId}/constancia"
)

int codigo = respuesta.getStatusCode()

KeywordUtil.logInfo("Código HTTP: ${codigo}")
KeywordUtil.logInfo("Content-Type: ${respuesta.getContentType()}")

// 3. Verificar acceso autorizado.
assert codigo == 200 :
	"Se esperaba HTTP 200, se obtuvo ${codigo}"

assert respuesta.getContentType()?.toLowerCase()?.contains('application/pdf') :
	'La respuesta no corresponde a un PDF.'

// 4. Verificar encabezado SHA-256.
String hash = respuesta.getHeaderField('X-Content-SHA256')

assert hash ==~ /[0-9a-fA-F]{64}/ :
	"El SHA-256 no es válido: ${hash}"

KeywordUtil.markPassed(
	'HU15_06: El auditor puede descargar la constancia aprobada.'
)
