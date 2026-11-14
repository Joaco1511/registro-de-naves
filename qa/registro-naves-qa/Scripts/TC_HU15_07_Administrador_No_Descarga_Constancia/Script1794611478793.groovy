import navesitas.Hu13 as H
import com.kms.katalon.core.util.KeywordUtil

Long naveId = 218L

// 1. Iniciar sesión como administrador.
def cookie = H.login('admin@navesitas.pa')

assert cookie != null :
	'No se pudo iniciar sesión como administrador.'

// 2. Intentar descargar la constancia.
def respuesta = H.pedir(
	cookie,
	"/api/naves/${naveId}/constancia"
)

int codigo = respuesta.getStatusCode()

KeywordUtil.logInfo("Código HTTP obtenido: ${codigo}")

// 3. Verificar que el acceso sea denegado.
assert codigo == 403 :
	"Se esperaba HTTP 403, pero se obtuvo HTTP ${codigo}"

KeywordUtil.markPassed(
	'HU15_07: El administrador no puede descargar constancias.'
)

