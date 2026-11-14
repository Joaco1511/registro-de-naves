import navesitas.Hu13 as H
import com.kms.katalon.core.util.KeywordUtil

Long naveId = 218L

String correoAjeno = 'agente.ajeno.1794612612756@navesitas.pa'

// 1. Iniciar sesión como agente ajeno.
def cookie = H.login(correoAjeno)

assert cookie != null :
	'No se pudo iniciar sesión como agente ajeno.'

// 2. Intentar descargar la constancia de otra nave.
def respuesta = H.pedir(
	cookie,
	"/api/naves/${naveId}/constancia"
)

int codigo = respuesta.getStatusCode()

KeywordUtil.logInfo("Código HTTP obtenido: ${codigo}")

// 3. Verificar denegación de acceso.
assert codigo == 403 :
	"Se esperaba HTTP 403, pero se obtuvo HTTP ${codigo}"

KeywordUtil.markPassed(
	'HU15_08: Un agente ajeno no puede descargar la constancia de otra nave.'
)