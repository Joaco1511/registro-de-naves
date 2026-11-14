import navesitas.Hu13 as H
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.util.KeywordUtil

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Arrays
import java.util.HexFormat
import java.util.Locale
import java.util.regex.Pattern

// =====================================================
// HU15_10 - Constancia con multiples propietarios
// y contenido suficiente para generar varias paginas.
// =====================================================

File archivo = null
def documento = null

try {
	// 1. Iniciar sesión como agente y funcionario DGMM.
	String agente = H.login('agente@navesitas.pa')
	String dgmm = H.login('revisor@navesitas.pa')

	assert agente != null : 'No se pudo iniciar sesión como agente.'
	assert dgmm != null : 'No se pudo iniciar sesión como funcionario DGMM.'

	String sufijo = System.currentTimeMillis().toString()

	// 2. Crear una nave nueva con eslora de 24 metros.
	def naveId = H.nave(agente, [
		nombre: 'HU15 Multipagina ' + sufijo,
		eslora: 24
	])

	assert naveId != null : 'No se pudo crear la nave.'

	KeywordUtil.logInfo("Nave creada para HU15_10: ${naveId}")

	// 3. Registrar 20 propietarios con nombres largos.
	// Cada propietario tendrá el 5% de participación.
	List<String> nombres = []

	(1..20).each { i ->

		String prefijo = "Copropietario ${String.format('%02d', i)} - "
		String relleno = 'Sociedad Maritima Internacional Panama Consorcio Naviero '

		String nombre = (prefijo + relleno.repeat(10)).substring(0, 150)

		// Evitar que el nombre termine en un espacio.
		if (nombre.endsWith(' ')) {
			nombre = nombre.substring(0, 149) + 'X'
		}

		assert nombre.length() == 150 :
			"El nombre del propietario ${i} no tiene 150 caracteres."

		def respuestaPropietario = H.pedir(
			agente,
			'/api/propietarios',
			[
				nombre: nombre,
				tipo: 'JURIDICA',
				nacionalidad: 'Panamena',
				domicilio: 'Ciudad de Panama',
				paisConstitucion: 'Panama',
				identificacion: "HU15-${sufijo}-${i}",
				naveId: naveId,
				porcentaje: 5.00
			]
		)

		WS.verifyResponseStatusCode(respuestaPropietario, 201)

		nombres.add(nombre)
	}

	// Confirmar que se registraron los 20 propietarios.
	def propietarios = H.json(
		H.pedir(agente, "/api/naves/${naveId}/propietarios")
	)

	assert propietarios.size() == 20 :
		"Se esperaban 20 propietarios y se encontraron ${propietarios.size()}."

	KeywordUtil.logInfo('Se registraron correctamente los 20 propietarios.')

	// 4. Designar agente residente.
	def respuestaAgente = H.pedir(
		agente,
		"/api/naves/${naveId}/agente-residente",
		[
			nombre: 'Bufete HU15 Multipagina',
			idoneidad: "IDN-HU15-${sufijo}",
			telefono: '+507 200-1234',
			correo: "hu15.${sufijo}@bufete.pa"
		]
	)

	WS.verifyResponseStatusCode(respuestaAgente, 201)

	// 5. Cargar documentos requeridos.
	[
		'CERTIFICADO_PROPIEDAD',
		'PODER_NOTARIAL',
		'ITC'
	].each { tipo ->
		H.cargar(agente, naveId, tipo)
	}

	// 6. Completar el flujo del trámite.
	WS.verifyResponseStatusCode(
		H.accion(dgmm, naveId, 'ADMITIR'), 200
	)

	WS.verifyResponseStatusCode(
		H.accion(dgmm, naveId, 'OBSERVAR'), 200
	)

	WS.verifyResponseStatusCode(
		H.accion(agente, naveId, 'SUBSANAR'), 200
	)

	// 7. Aprobar todos los documentos.
	def docs = H.json(
		H.pedir(dgmm, "/api/naves/${naveId}/documentos")
	)

	assert docs.size() == 3 :
		"Se esperaban 3 documentos y se encontraron ${docs.size()}."

	docs.each { d ->
		def revision = H.pedir(
			dgmm,
			"/api/naves/${naveId}/documentos/${d.id}/revision",
			[decision: 'APROBAR']
		)

		WS.verifyResponseStatusCode(revision, 200)
	}

	// 8. Aprobar definitivamente el trámite.
	def aprobacion = H.accion(dgmm, naveId, 'APROBAR')

	WS.verifyResponseStatusCode(aprobacion, 200)

	KeywordUtil.logInfo('Trámite aprobado correctamente.')

	// 9. Descargar la constancia como propietario.
	def respuesta = H.pedir(
		agente,
		"/api/naves/${naveId}/constancia"
	)

	WS.verifyResponseStatusCode(respuesta, 200)

	String contentType = respuesta.getContentType()
	String hashServidor = respuesta.getHeaderField('X-Content-SHA256')

	assert contentType?.toLowerCase()?.contains('application/pdf') :
		"El servidor no devolvió un PDF: ${contentType}"

	assert hashServidor ==~ /[0-9a-fA-F]{64}/ :
		'El encabezado SHA-256 no es válido.'

	archivo = File.createTempFile('hu15-multipagina-', '.pdf')
	respuesta.writeResponseBodyToFile(archivo.absolutePath)

	assert archivo.length() > 0 :
		'La constancia descargada está vacía.'

	// 10. Comprobar la firma y la integridad del PDF.
	byte[] bytes = Files.readAllBytes(archivo.toPath())

	String firma = new String(
		Arrays.copyOfRange(bytes, 0, 5),
		StandardCharsets.US_ASCII
	)

	assert firma == '%PDF-' :
		'El archivo descargado no tiene una firma PDF válida.'

	String hashLocal = HexFormat.of().formatHex(
		MessageDigest.getInstance('SHA-256').digest(bytes)
	)

	assert hashLocal.equalsIgnoreCase(hashServidor) :
		'El SHA-256 local no coincide con el del servidor.'

	// 11. Abrir el PDF y extraer su contenido.
	documento = Loader.loadPDF(archivo)

	PDFTextStripper stripper = new PDFTextStripper()
	String texto = stripper.getText(documento)

	assert texto?.trim() :
		'No se pudo extraer texto de la constancia.'

	int paginas = documento.getNumberOfPages()

	KeywordUtil.logInfo("Páginas generadas: ${paginas}")

	// La prueba debe producir al menos dos páginas.
	assert paginas >= 2 :
		"Se esperaba un PDF multipágina, pero solo tiene ${paginas} página(s)."

	// 12. Normalizar texto para facilitar las comparaciones.
	String normalizado = Normalizer.normalize(
		texto,
		Normalizer.Form.NFD
	).replaceAll('\\p{M}+', '')
	 .replaceAll('\\s+', ' ')
	 .toLowerCase(Locale.ROOT)

	// Eliminamos espacios para poder comprobar nombres
	// aunque PDFBox los divida entre varias líneas.
	String compacto = normalizado.replaceAll('\\s+', '')

	// 13. Comprobar datos generales de la nave.
	assert normalizado.contains("numero de registro: ${naveId}".toLowerCase()) :
		'No aparece el número de registro de la nave.'

	assert normalizado.contains('nombre: hu15 multipagina') :
		'No aparece el nombre de la nave.'

	assert normalizado.contains('tipo: carga') :
		'No aparece el tipo de nave.'

	assert normalizado.contains('propietarios y participacion') :
		'No aparece la sección de propietarios.'

	assert normalizado.contains('agente residente vigente') :
		'No aparece la sección del agente residente.'

	// 14. Verificar los 20 nombres y sus respectivos porcentajes.
	nombres.eachWithIndex { nombre, indice ->

		String nombreCompacto = nombre
			.toLowerCase(Locale.ROOT)
			.replaceAll('\\s+', '')

		assert compacto.contains(nombreCompacto) :
			"No se encontró el nombre completo del propietario ${indice + 1}."

		String patron = Pattern.quote(nombreCompacto) + '-5\\.00%'

		assert Pattern.compile(patron).matcher(compacto).find() :
			"No se encontró el porcentaje del propietario ${indice + 1}."
	}

	KeywordUtil.logInfo(
		'Se encontraron los 20 propietarios con su participación del 5%.'
	)

	// 15. Comprobar el agente residente.
	assert normalizado.contains('bufete hu15 multipagina') :
		'No aparece el agente residente.'

	// 16. Comprobar la aprobación.
	assert normalizado.contains(
		'estado del tramite: aprobada. nave registrada.'
	) :
		'No aparece el estado de aprobación.'

	assert Pattern.compile(
		'fecha de aprobacion:\\s*\\d{2}/\\d{2}/\\d{4}'
	).matcher(normalizado).find() :
		'No aparece la fecha de aprobación.'

	// 17. Comprobar la advertencia legal.
	assert normalizado.contains(
		'no constituye patente de navegacion ni certificacion oficial'
	) :
		'No aparece completa la advertencia legal.'

	assert normalizado.contains('sin firma digital') :
		'No aparece la indicación de ausencia de firma digital.'

	KeywordUtil.markPassed(
		"HU15_10: PDF multipágina correcto. ${paginas} páginas, 20 propietarios, porcentajes e integridad SHA-256 verificados."
	)

} finally {

	if (documento != null) {
		documento.close()
	}

	if (archivo != null && archivo.exists()) {
		archivo.delete()
	}
}