import navesitas.Hu13 as H
import com.kms.katalon.core.util.KeywordUtil

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern
import java.nio.file.Files

Long naveId = 218L

File archivo = null
def documento = null

try {
	// 1. Iniciar sesión como propietario.
	def cookie = H.login('agente@navesitas.pa')

	assert cookie != null :
		'No se pudo iniciar sesión como propietario.'

	// 2. Descargar la constancia.
	def respuesta = H.pedir(
		cookie,
		"/api/naves/${naveId}/constancia"
	)

	assert respuesta.getStatusCode() == 200 :
		"Se esperaba HTTP 200. Obtenido: ${respuesta.getStatusCode()}"

	// 3. Guardar el PDF temporalmente.
	archivo = File.createTempFile('hu15-contenido-', '.pdf')
	respuesta.writeResponseBodyToFile(archivo.absolutePath)

	assert archivo.length() > 0 :
		'El archivo PDF está vacío.'

	// 4. Extraer el texto con PDFBox 3.
	documento = Loader.loadPDF(archivo)

	PDFTextStripper stripper = new PDFTextStripper()
	String texto = stripper.getText(documento)

	assert texto?.trim() :
		'No se pudo extraer texto del PDF.'

	// Normalizar acentos y espacios para facilitar comparaciones.
	String normalizado = Normalizer.normalize(
		texto,
		Normalizer.Form.NFD
	).replaceAll('\\p{M}+', '')
	 .replaceAll('\\s+', ' ')
	 .toLowerCase(Locale.ROOT)

	KeywordUtil.logInfo(
		"Páginas de la constancia: ${documento.getNumberOfPages()}"
	)

	// 5. Verificar identificación y datos de la nave.
	def campos = [
		'numero de registro: 218',
		'nombre: hu trece katalon 68732054570399',
		'tipo: carga',
		'datos de la nave',
		'propietarios y participacion',
		'agente residente vigente'
	]

	campos.each { campo ->
		assert normalizado.contains(campo) :
			"No se encontró el campo esperado: ${campo}"
	}

	// 6. Verificar valores técnicos.
	def valoresTecnicos = [
		'arqueo bruto:\\s*499(?:\\.0+)?\\s*gt',
		'tonelaje neto:\\s*100(?:\\.0+)?\\s*trn',
		'eslora:\\s*24(?:\\.0+)?\\s*m',
		'manga:\\s*10(?:\\.0+)?\\s*m',
		'puntal:\\s*5(?:\\.0+)?\\s*m'
	]

	valoresTecnicos.each { expresion ->
		assert Pattern.compile(expresion)
			.matcher(normalizado).find() :
			"No se encontró el valor técnico: ${expresion}"
	}

	// 7. Verificar propietario y porcentaje.
	assert normalizado.contains('naviera hu trece') :
		'No aparece el propietario de la nave.'

	assert Pattern.compile(
		'naviera hu trece\\s*-\\s*\\d+(?:\\.\\d+)?\\s*%'
	).matcher(normalizado).find() :
		'No se encontró el porcentaje de participación del propietario.'

	// 8. Verificar agente residente.
	assert normalizado.contains('nombre: bufete hu trece') :
		'No aparece el agente residente.'

	assert normalizado.contains('idoneidad: idn-68738251714200') :
		'No aparece la idoneidad del agente residente.'

	// 9. Verificar aprobación.
	assert normalizado.contains(
		'estado del tramite: aprobada. nave registrada.'
	) :
		'No se encontró el estado aprobado del trámite.'

	assert Pattern.compile(
		'fecha de aprobacion:\\s*\\d{2}/\\d{2}/\\d{4}'
	).matcher(normalizado).find() :
		'No se encontró la fecha de aprobación.'

	// Confirmar que el funcionario tenga un nombre registrado.
	String lineaFuncionario = texto.readLines().find {
		it.toLowerCase().contains('funcionario que aprobó:')
	}

	assert lineaFuncionario != null :
		'No aparece el funcionario que aprobó.'

	assert lineaFuncionario.split(':', 2).length == 2 &&
		   lineaFuncionario.split(':', 2)[1].trim().length() > 0 :
		'El nombre del funcionario aprobador está vacío.'

	// 10. Verificar advertencia legal.
	String advertencia =
		'constancia digital del tramite de registro. ' +
		'no constituye patente de navegacion ni certificacion oficial. ' +
		'sin firma digital.'

	assert normalizado.contains(advertencia) :
		'La advertencia legal está incompleta o no aparece.'

	// 11. Verificar que se conservan caracteres acentuados.
	assert texto.contains('navegación') :
		'No se encontró correctamente el carácter acentuado en navegación.'

	assert texto.contains('aprobación') :
		'No se encontró correctamente el carácter acentuado en aprobación.'

	KeywordUtil.markPassed(
		'HU15_09: El contenido del PDF contiene los datos de la nave, propietario, agente residente, aprobación y advertencia legal.'
	)

} finally {
	if (documento != null) {
		documento.close()
	}

	if (archivo != null && archivo.exists()) {
		archivo.delete()
	}
}

