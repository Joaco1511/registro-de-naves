import navesitas.Hu13 as H

import com.kms.katalon.core.testobject.TestObject
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI

// Crea objetos de prueba usando selectores CSS.
TestObject porCss(String nombre, String selector) {
	TestObject objeto = new TestObject(nombre)
	objeto.addProperty('css', ConditionType.EQUALS, selector)
	return objeto
}

String baseUrl = 'http://localhost:8080'
String correoAgente = 'agente@navesitas.pa'
String clave = System.getenv('NAVESITAS_CLAVE') ?: 'Navesitas2026*'

boolean navegadorAbierto = false

try {
	// --------------------------------------------------
	// 1. Preparar una nave mediante la API
	// --------------------------------------------------

	WebUI.comment('Preparando nave de prueba para HU-13_04...')

	def cookieAgente = H.login(correoAgente)

	def naveId = H.nave(cookieAgente, [
		tipo: 'CARGA',
		servicio: 'INTERNACIONAL',
		eslora: 23.99,
		tonelajeBruto: 499
	])

	assert naveId != null : 'No se pudo crear la nave de prueba.'

	H.preparar(cookieAgente, naveId)

	WebUI.comment("Nave preparada. ID: ${naveId}")

	// --------------------------------------------------
	// 2. Abrir el navegador e iniciar sesión
	// --------------------------------------------------

	WebUI.openBrowser('')
	navegadorAbierto = true

	WebUI.maximizeWindow()
	WebUI.navigateToUrl("${baseUrl}/login.html")

	TestObject campoCorreo = porCss('campoCorreo', '#correo')
	TestObject campoClave = porCss('campoClave', '#password')
	TestObject botonEntrar = porCss('botonEntrar', '#btn-entrar')

	WebUI.waitForElementVisible(campoCorreo, 15)

	WebUI.setText(campoCorreo, correoAgente)
	WebUI.setText(campoClave, clave)
	WebUI.click(botonEntrar)

	WebUI.waitForPageLoad(15)
	WebUI.delay(2)

	// --------------------------------------------------
	// 3. Abrir directamente el expediente de la nave
	// --------------------------------------------------

	WebUI.navigateToUrl(
		"${baseUrl}/expediente.html?naveId=${naveId}"
	)

	TestObject seccionDocumentos = porCss(
		'seccionDocumentos',
		'#seccion-documentos'
	)

	TestObject requisitos = porCss(
		'requisitosDocumentales',
		'#requisitos-documentales'
	)

	TestObject primerRequisito = porCss(
		'primerRequisito',
		'#requisitos-documentales li'
	)

	WebUI.waitForElementVisible(seccionDocumentos, 20)
	WebUI.waitForElementVisible(requisitos, 20)
	WebUI.waitForElementPresent(primerRequisito, 30)

	// --------------------------------------------------
	// 4. Obtener los requisitos que muestra la interfaz
	// --------------------------------------------------

	def resultado = WebUI.executeJavaScript(
		"""
        return Array.from(
            document.querySelectorAll('#requisitos-documentales li')
        ).map(elemento => elemento.innerText.trim());
        """,
		null
	)

	List<String> lineas = []

	if (resultado instanceof Collection) {
		lineas = resultado.collect { it.toString() }
	} else if (resultado != null) {
		lineas = resultado.toString().split('\\n').toList()
	}

	WebUI.comment('Requisitos mostrados en la interfaz:')

	lineas.each { linea ->
		WebUI.comment(linea)
	}

	// --------------------------------------------------
	// 5. Verificar ITC y SMC
	// --------------------------------------------------

	String lineaITC = lineas.find {
		it.toUpperCase().contains('ITC')
	}

	String lineaSMC = lineas.find {
		it.toUpperCase().contains('SMC')
	}

	assert lineaITC != null :
		'No se encontró el requisito ITC en la interfaz.'

	assert lineaSMC != null :
		'No se encontró el requisito SMC en la interfaz.'

	assert lineaITC.toLowerCase().contains('no aplica') :
		"El ITC no aparece como 'No aplica'. Texto: ${lineaITC}"

	assert lineaSMC.toLowerCase().contains('no aplica') :
		"El SMC no aparece como 'No aplica'. Texto: ${lineaSMC}"

	WebUI.comment('HU-13_04: ITC y SMC aparecen como No aplica.')

} finally {
	if (navegadorAbierto) {
		WebUI.closeBrowser()
	}
}