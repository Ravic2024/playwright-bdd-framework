package com.qa.framework.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe Excel reader/writer for parallel execution.
 *
 * - One ReentrantReadWriteLock per workbook file: many threads may read at once,
 *   but a write gets exclusive access, so no thread ever reads a half-written file.
 * - No Workbook is cached or shared between threads; each call opens and closes its own.
 * - Writes go to a temp file first and are then moved over the original, so the
 *   workbook is never left corrupted if something fails mid-write.
 *
 * Sheet convention: row 0 = header row; one column is used as a unique key (e.g. TestCaseId).
 */
public final class ExcelUtil {

    private static final Logger LOG = LogManager.getLogger(ExcelUtil.class);
    private static final Map<String, ReentrantReadWriteLock> LOCKS = new ConcurrentHashMap<>();
    private static final DataFormatter FORMATTER = new DataFormatter();

    private ExcelUtil() {
    }

    private static ReentrantReadWriteLock lockFor(String filePath) {
        String key = new File(filePath).getAbsoluteFile().toPath().normalize().toString();
        return LOCKS.computeIfAbsent(key, k -> new ReentrantReadWriteLock(true));
    }

    // ------------------------------------------------------------------ READ

    /** Returns every data row of the sheet as an ordered map of header -> cell value. */
    public static List<Map<String, String>> getAllRows(String filePath, String sheetName) {
        ReentrantReadWriteLock lock = lockFor(filePath);
        lock.readLock().lock();
        try (InputStream in = new FileInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = getSheet(workbook, sheetName);
            List<String> headers = readHeaders(sheet);
            List<Map<String, String>> rows = new ArrayList<>();

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                Map<String, String> rowData = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    rowData.put(headers.get(c), FORMATTER.formatCellValue(row.getCell(c)).trim());
                }
                rows.add(rowData);
            }
            return rows;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Excel: " + filePath, e);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Returns the single row whose keyColumn equals keyValue. */
    public static Map<String, String> getRowData(String filePath, String sheetName,
                                                 String keyColumn, String keyValue) {
        return getAllRows(filePath, sheetName).stream()
                .filter(row -> keyValue.equalsIgnoreCase(row.get(keyColumn)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No row with " + keyColumn + "='" + keyValue + "' in sheet '" + sheetName + "'"));
    }

    public static String getCellData(String filePath, String sheetName,
                                     String keyColumn, String keyValue, String columnName) {
        String value = getRowData(filePath, sheetName, keyColumn, keyValue).get(columnName);
        if (value == null) {
            throw new IllegalArgumentException("Column '" + columnName + "' not found in sheet '" + sheetName + "'");
        }
        return value;
    }

    // ----------------------------------------------------------------- WRITE

    /**
     * Writes a value into columnName of the row identified by keyColumn = keyValue.
     * The column is created at the end of the header row if it does not exist.
     */
    public static void writeCellData(String filePath, String sheetName, String keyColumn,
                                     String keyValue, String columnName, String value) {
        ReentrantReadWriteLock lock = lockFor(filePath);
        lock.writeLock().lock();
        try {
            Workbook workbook;
            try (InputStream in = new FileInputStream(filePath)) {
                workbook = WorkbookFactory.create(in);
            }

            try (workbook) {
                Sheet sheet = getSheet(workbook, sheetName);
                List<String> headers = readHeaders(sheet);

                int keyCol = headers.indexOf(keyColumn);
                if (keyCol < 0) {
                    throw new IllegalArgumentException("Key column '" + keyColumn + "' not found in sheet '" + sheetName + "'");
                }

                int targetCol = headers.indexOf(columnName);
                if (targetCol < 0) {
                    targetCol = headers.size();
                    sheet.getRow(0).createCell(targetCol).setCellValue(columnName);
                }

                Row targetRow = null;
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row != null && keyValue.equalsIgnoreCase(FORMATTER.formatCellValue(row.getCell(keyCol)).trim())) {
                        targetRow = row;
                        break;
                    }
                }
                if (targetRow == null) {
                    throw new IllegalArgumentException("No row with " + keyColumn + "='" + keyValue + "' in sheet '" + sheetName + "'");
                }

                Cell cell = targetRow.getCell(targetCol);
                if (cell == null) {
                    cell = targetRow.createCell(targetCol);
                }
                cell.setCellValue(value);

                saveAtomically(workbook, filePath);
            }
            LOG.info("Excel write: [{}] {}={} -> {}='{}'", sheetName, keyColumn, keyValue, columnName, value);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Excel: " + filePath, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Creates a new workbook with a header row (handy for result/output sheets). */
    public static void createWorkbook(String filePath, String sheetName, List<String> headers) {
        ReentrantReadWriteLock lock = lockFor(filePath);
        lock.writeLock().lock();
        try (Workbook workbook = new XSSFWorkbook()) {
            Row header = workbook.createSheet(sheetName).createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            File parent = new File(filePath).getAbsoluteFile().getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            saveAtomically(workbook, filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create Excel: " + filePath, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // --------------------------------------------------------------- helpers

    private static void saveAtomically(Workbook workbook, String filePath) throws IOException {
        Path target = new File(filePath).getAbsoluteFile().toPath();
        Path temp = Files.createTempFile(target.getParent(), "excel-", ".tmp");
        try (OutputStream out = new FileOutputStream(temp.toFile())) {
            workbook.write(out);
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicNotSupported) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Sheet getSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet not found: " + sheetName);
        }
        return sheet;
    }

    private static List<String> readHeaders(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new IllegalStateException("Header row missing in sheet: " + sheet.getSheetName());
        }
        List<String> headers = new ArrayList<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            headers.add(FORMATTER.formatCellValue(headerRow.getCell(c)).trim());
        }
        return headers;
    }

    private static boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c >= 0 && c < row.getLastCellNum(); c++) {
            if (!FORMATTER.formatCellValue(row.getCell(c)).isBlank()) {
                return false;
            }
        }
        return true;
    }
}
