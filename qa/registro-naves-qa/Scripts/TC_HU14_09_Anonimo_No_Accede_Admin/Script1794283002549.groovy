import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS

import navesitas.Http
import groovy.json.JsonOutput

// ==========================================
// CONFIGURACION
// ==========================================

String base = 'http://localhost:8080'

String correoNuevo = 'hu14.anonimo.' +
	System.currentTimeMillis() + '@navesitas.pa'

// ==========================================
// 1. PREPARAR USUARIO ADMINISTRATIVO
// ==========================================

Map cuerpo = [
	correo: correoNuevo,
	password: 'PasswordPrueba2026',
	nombreCompleto: 'Intento Anonimo Katalon',
	rol: 'FUNCIONARIO_DGMM'
]

// ==========================================
// 2. CREAR SOLICITUD SIN AUTENTICACION
// ==========================================

RequestObject alta = new RequestObject('altaAnonima')

alta.setRestUrl(base + '/api/admin/usuarios')
alta.setRestRequestMethod('POST')

// IMPORTANTE:
// No agregamos Cookie ni JSESSIONID.
// Http.enviar agregara automaticamente CSRF.

alta.setHttpHeaderProperties([
	new TestObjectProperty(
		'Content-Type',
		ConditionType.EQUALS,
		'application/json'
	)
])

alta.setBodyContent(
	new HttpTextBodyContent(JsonOutput.toJson(cuerpo))
)

// ==========================================
// 3. ENVIAR SOLICITUD
// ==========================================

ResponseObject r = Http.enviar(alta)

println('======================================')
println('INTENTO DE ACCESO ANONIMO')
println('======================================')

println('Correo: ' + correoNuevo)
println('Status: ' + r.getStatusCode())
println('Respuesta: ' + r.getResponseBodyContent())

// ==========================================
// 4. VERIFICAR AUTENTICACION OBLIGATORIA
// ==========================================

WS.verifyResponseStatusCode(r, 401)

println('TC_HU14_09 completado correctamente.')


