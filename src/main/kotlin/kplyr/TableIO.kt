package kplyr

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord
import java.io.*
import java.util.zip.GZIPInputStream

/**
Methods to read and write tables into/from DataFrames
 * see also https://commons.apache.org/proper/commons-csv/ for other implementations
 * https://github.com/databricks/spark-csv
 * https://examples.javacodegeeks.com/core-java/apache/commons/csv-commons/writeread-csv-files-with-apache-commons-csv-example/

 */


fun DataFrame.Companion.fromCSV(file: String) = fromCSV(File(file))

fun DataFrame.Companion.fromTSV(file: String) = fromCSV(File(file), format = CSVFormat.TDF)

// http://stackoverflow.com/questions/9648811/specific-difference-between-bufferedreader-and-filereader
fun DataFrame.Companion.fromCSV(file: File,
                                format: CSVFormat = CSVFormat.DEFAULT,
                                isCompressed: Boolean = file.name.endsWith(".gz")): DataFrame {

    val bufReader = if (isCompressed) {
        // http://stackoverflow.com/questions/1080381/gzipinputstream-reading-line-by-line
        val gzip = GZIPInputStream(FileInputStream(file));
        BufferedReader(InputStreamReader(gzip));
    } else {
        BufferedReader(FileReader(file))
    }

    return fromCSV(bufReader, format)
}

//http://stackoverflow.com/questions/5200187/convert-inputstream-to-bufferedreader
fun DataFrame.Companion.fromCSV(inStream: InputStream, format: CSVFormat = CSVFormat.DEFAULT) =
        fromCSV(BufferedReader(InputStreamReader(inStream, "UTF-8")), format)


@Suppress("unused")
fun DataFrame.Companion.fromCSV(reader: Reader, format: CSVFormat = CSVFormat.DEFAULT): DataFrame {
    val csvParser = format.withFirstRecordAsHeader().parse(reader)
    val records = csvParser.iterator().asSequence().toList()


    val numRows = records.toList().size
    val cols = mutableListOf<DataCol>()
    // fixme: super-inefficient because of incorrect loop order
    for (colName in csvParser.headerMap.keys) {
        when { // when without arg see https://kotlinlang.org/docs/reference/control-flow.html#when-expression
            isDoubleCol(colName, records) -> DoubleCol(colName, Array(numRows, { records[it][colName].toDoubleOrNull() }).toList())
            isIntCol(colName, records) -> IntCol(colName, Array(numRows, { records[it][colName].toIntOrNull() }).toList())
            isBoolCol(colName, records) -> BooleanCol(colName, Array(numRows, { records[it][colName].toAsBoolNull() }).toList())
            else -> StringCol(colName, Array(numRows, { records[it][colName].naAsNull() }).toList())
        }.let { cols.add(it) }
    }

    return SimpleDataFrame(cols)
}

// we use null to encode NA // todo consider NaN
// NA aware conversions
fun String.toDoubleOrNull(): Double? = if (this == "NA") null else this.toDouble()

fun String.toIntOrNull(): Int? = if (this == "NA") null else this.toInt()
fun String.toAsBoolNull(): Boolean? = if (this == "NA") null else this.toBoolean()
fun String.naAsNull(): String? = if (this == "NA") null else this

internal fun isDoubleCol(colName: String?, records: List<CSVRecord>, peekSize: Int = 5): Boolean {
    try {
        records.take(peekSize).mapIndexed { rowIndex, csvRecord -> records[rowIndex][colName].toDoubleOrNull() }
    } catch(e: NumberFormatException) {
        return false
    }

    return true
}

internal fun isIntCol(colName: String?, records: List<CSVRecord>, peekSize: Int = 5): Boolean {
    try {
        records.take(peekSize).mapIndexed { rowIndex, csvRecord -> records[rowIndex][colName].toIntOrNull() }
    } catch(e: NumberFormatException) {
        return false
    }

    return true
}

internal fun isBoolCol(colName: String?, records: List<CSVRecord>, peekSize: Int = 5): Boolean {
    try {
        records.take(peekSize).mapIndexed { rowIndex, csvRecord ->
            var cellValue = records[rowIndex][colName].toLowerCase()

            // remap some obvious ones
            cellValue = if (cellValue == "NA") "false" else cellValue // fixme add missing value support
            cellValue = if (cellValue == "F") "false" else cellValue
            cellValue = if (cellValue == "T") "true" else cellValue


            if (listOf("true", "false", "T", "F").contains(cellValue)) throw  NumberFormatException("invalid boolean cell value")

            return cellValue.toAsBoolNull() ?: true
        }

    } catch(e: NumberFormatException) {
        return false
    }

    return true
}


//todo add support for compressed writing
fun DataFrame.writeCSV(file: String, format: CSVFormat = CSVFormat.DEFAULT) = writeCSV(File(file), format)

fun DataFrame.writeCSV(file: File, format: CSVFormat = CSVFormat.DEFAULT) {
    //initialize FileWriter object
    val fileWriter = FileWriter(file)

    //initialize CSVPrinter object
    val csvFilePrinter = CSVPrinter(fileWriter, format)

    //Create CSV file header
    csvFilePrinter.printRecord(names)

    // write records
    for (record in rows) {
        csvFilePrinter.printRecord(record.values)
    }

    fileWriter.flush()
    fileWriter.close()
    csvFilePrinter.close()
}

fun main(args: Array<String>) {
    val fromCSV = DataFrame.fromCSV("/Users/brandl/projects/kotlin/kplyr/src/test/resources/kplyr/data/msleep.csv")
    fromCSV.writeCSV("/Users/brandl/Desktop/test.csv", CSVFormat.TDF)

//    fromCSV.print()
//    fromCSV.glimpse()
}