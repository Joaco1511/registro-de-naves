import navesitas.Hu13 as H
import com.kms.katalon.core.util.KeywordUtil

// 1. Iniciar sesión como agente naviero.
def cookie = H.login('agente@navesitas.pa')

assert cookie != null :
	'No se pudo iniciar sesión como agente naviero.'

// 2. Crear una nave de prueba.
def naveId = H.nave(cookie, [
	tipo: 'CARGA',
	servicio: 'INTERNACIONAL',
	eslora: 23.99,
	tonelajeBruto: 499
])

assert naveId != null :
	'No se pudo crear la nave de prueba.'

// 3. Preparar propietario y agente residente.
H.preparar(cookie, naveId)

KeywordUtil.logInfo("Nave de prueba creada: ${naveId}")

// 4. Intentar descargar la constancia.
def respuesta = H.pedir(
	cookie,
	"/api/naves/${naveId}/constancia"
)

int codigo = respuesta.getStatusCode()

KeywordUtil.logInfo("Código HTTP obtenido: ${codigo}")

// 5. Verificar que no se emita la constancia.
assert codigo == 409 :
	"Se esperaba HTTP 409, pero se obtuvo HTTP ${codigo}"

KeywordUtil.markPassed(
	'HU15_03: El sistema impide emitir constancias de trámites no aprobados.'
)