import navesitas.Hu13 as H
import com.kms.katalon.core.util.KeywordUtil

// Intentar descargar una constancia sin iniciar sesión.
def respuesta = H.pedir(
	null,
	'/api/naves/999999999/constancia'
)

int codigo = respuesta.getStatusCode()

KeywordUtil.logInfo("Código HTTP obtenido: ${codigo}")

assert codigo == 401 :
	"Se esperaba HTTP 401, pero se obtuvo HTTP ${codigo}"

KeywordUtil.markPassed(
	"HU15_01: El sistema impide descargar constancias sin sesión."
)

