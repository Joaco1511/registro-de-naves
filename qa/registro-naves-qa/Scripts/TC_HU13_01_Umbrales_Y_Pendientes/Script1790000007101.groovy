import navesitas.Hu13 as H
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.mobile.keyword.MobileBuiltInKeywords as Mobile
import com.kms.katalon.core.cucumber.keyword.CucumberBuiltinKeywords as CucumberKW
import com.kms.katalon.core.windows.keyword.WindowsBuiltinKeywords as Windows
import com.kms.katalon.core.llm.keyword.LlmKeywords as LLM
import static com.kms.katalon.core.testobject.ObjectRepository.findTestObject
import static com.kms.katalon.core.testobject.ObjectRepository.findWindowsObject
import static com.kms.katalon.core.testdata.TestDataFactory.findTestData
import static com.kms.katalon.core.testcase.TestCaseFactory.findTestCase
import static com.kms.katalon.core.checkpoint.CheckpointFactory.findCheckpoint
import com.kms.katalon.core.model.FailureHandling as FailureHandling
import com.kms.katalon.core.testcase.TestCase as TestCase
import com.kms.katalon.core.testdata.TestData as TestData
import com.kms.katalon.core.testobject.TestObject as TestObject
import com.kms.katalon.core.checkpoint.Checkpoint as Checkpoint
import org.openqa.selenium.Keys as Keys
import internal.GlobalVariable as GlobalVariable

String agente = H.login('agente@navesitas.pa')

def casos = [[[('eslora') : 23.99], 'NO_APLICA', 'NO_APLICA'], [[('eslora') : 24], 'OBLIGATORIO', 'NO_APLICA'], [[('eslora') : 24.01]
        , 'OBLIGATORIO', 'NO_APLICA'], [[('tonelajeBruto') : 499], 'NO_APLICA', 'NO_APLICA'], [[('tonelajeBruto') : 500]
        , 'NO_APLICA', 'OBLIGATORIO'], [[('tonelajeBruto') : 501], 'NO_APLICA', 'OBLIGATORIO'], [[('tipo') : 'PASAJEROS'
            , ('tonelajeBruto') : 100], 'NO_APLICA', 'OBLIGATORIO'], [[('servicio') : 'CABOTAJE'], 'REQUIERE_REVISION', 'REQUIERE_REVISION']
    , [[('servicio') : 'MIXTO'], 'REQUIERE_REVISION', 'REQUIERE_REVISION'], [[('tipo') : 'MIXTA'], 'REQUIERE_REVISION', 'REQUIERE_REVISION']
    , [[('tipo') : 'TANQUERO'], 'REQUIERE_REVISION', 'REQUIERE_REVISION']]


casos.eachWithIndex({ def caso, int indice ->

	def id = H.nave(agente, caso[0])

	def respuesta = H.pedir(
		agente,
		'/api/naves/' + id + '/documentos/requisitos'
	)

	WS.verifyResponseStatusCode(respuesta, 200)

	def requisitos = H.json(respuesta).collectEntries({
		[(it.tipo): it]
	})

	println('======================================')
	println('ESCENARIO HU13 - ' + (indice + 1))
	println('Cambios aplicados: ' + caso[0])
	println('Nave ID: ' + id)

	println('ITC esperado: ' + caso[1])
	println('ITC obtenido: ' + requisitos.ITC.estado)

	println('SMC esperado: ' + caso[2])
	println('SMC obtenido: ' + requisitos.SMC.estado)

	println('======================================')

	assert requisitos.ITC.estado == caso[1]
	assert requisitos.SMC.estado == caso[2]

	assert requisitos.CERTIFICADO_PROPIEDAD.estado ==
		'OBLIGATORIO'

	assert requisitos.PODER_NOTARIAL.estado ==
		'OBLIGATORIO'

	assert requisitos.values().every({
		it.explicacion
	})

})


