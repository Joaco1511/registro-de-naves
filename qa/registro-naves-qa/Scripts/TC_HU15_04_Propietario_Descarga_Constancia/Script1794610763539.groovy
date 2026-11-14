import navesitas.Hu13 as H
import com.kms.katalon.core.util.KeywordUtil

import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Arrays
import java.util.HexFormat

// Identificador de la nave aprobada en HU13_02.
Long naveId = 218L

File archivo1 = null
File archivo2 = null

try {
	// 1. Iniciar sesión como propietario de la nave.
	def cookie = H.login('agente@navesitas.pa')

	assert cookie != null :
		'No se pudo iniciar sesión como agente propietario.'

	// 2. Primera descarga.
	def primera = H.pedir(
		cookie,
		"/api/naves/${naveId}/constancia"
	)

	assert primera.getStatusCode() == 200 :
		"Se esperaba HTTP 200. Obtenido: ${primera.getStatusCode()}"

	// 3. Verificar encabezados.
	String contentType = primera.getContentType()
	String disposition = primera.getHeaderField('Content-Disposition')
	String hashServidor = primera.getHeaderField('X-Content-SHA256')

	assert contentType?.toLowerCase()?.contains('application/pdf') :
		"Tipo de contenido incorrecto: ${contentType}"

	assert disposition?.contains("constancia-registro-${naveId}.pdf") :
		"Nombre de archivo incorrecto: ${disposition}"

	assert hashServidor ==~ /[0-9a-fA-F]{64}/ :
		"El encabezado SHA-256 no es válido: ${hashServidor}"

	assert primera.getResponseBodySize() > 0 :
		'El PDF recibido está vacío.'

	// 4. Guardar el PDF original en un archivo temporal.
	archivo1 = File.createTempFile('hu15-primera-', '.pdf')
	primera.writeResponseBodyToFile(archivo1.absolutePath)

	byte[] bytes1 = Files.readAllBytes(archivo1.toPath())

	// Comprobar la firma característica de un PDF.
	String firmaPDF = new String(
		Arrays.copyOfRange(bytes1, 0, 5),
		StandardCharsets.US_ASCII
	)

	assert firmaPDF == '%PDF-' :
		"El archivo no tiene una firma PDF válida: ${firmaPDF}"

	// Calcular SHA-256 localmente.
	String hashLocal = HexFormat.of().formatHex(
		MessageDigest.getInstance('SHA-256').digest(bytes1)
	)

	assert hashLocal.equalsIgnoreCase(hashServidor) :
		'El SHA-256 del archivo no coincide con el encabezado del servidor.'

	KeywordUtil.logInfo("Primera descarga correcta. SHA-256: ${hashServidor}")

	// 5. Realizar una segunda descarga.
	def segunda = H.pedir(
		cookie,
		"/api/naves/${naveId}/constancia"
	)

	assert segunda.getStatusCode() == 200 :
		"La segunda descarga devolvió HTTP ${segunda.getStatusCode()}"

	String hashSegundo = segunda.getHeaderField('X-Content-SHA256')

	assert hashSegundo == hashServidor :
		'El SHA-256 cambió entre descargas.'

	archivo2 = File.createTempFile('hu15-segunda-', '.pdf')
	segunda.writeResponseBodyToFile(archivo2.absolutePath)

	byte[] bytes2 = Files.readAllBytes(archivo2.toPath())

	// 6. Comparar los bytes de ambas descargas.
	assert Arrays.equals(bytes1, bytes2) :
		'El contenido PDF cambió entre la primera y segunda descarga.'

	KeywordUtil.markPassed(
		'HU15_04: Descarga PDF correcta. Encabezados, firma y SHA-256 válidos. Los bytes permanecen idénticos.'
	)

} finally {
	// Eliminar los archivos temporales.
	if (archivo1 != null) archivo1.delete()
	if (archivo2 != null) archivo2.delete()
}
