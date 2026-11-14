import navesitas.Hu13 as H
import com.kms.katalon.core.util.KeywordUtil

// Iniciar sesión como agente naviero.
def cookie = H.login('agente@navesitas.pa')

assert cookie != null :
	'No se pudo iniciar sesión como agente naviero.'

// Consultar una nave inexistente.
def respuesta = H.pedir(
	cookie,
	'/api/naves/999999999/constancia'
)

int codigo = respuesta.getStatusCode()

KeywordUtil.logInfo("Código HTTP obtenido: ${codigo}")

assert codigo == 404 :
	"Se esperaba HTTP 404, pero se obtuvo HTTP ${codigo}"

KeywordUtil.markPassed(
	"HU15_02: El sistema responde 404 para una nave inexistente."
)