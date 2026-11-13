package com.ris.rms.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ris.rms.entity.Department;
import com.ris.rms.repository.DepartmentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Generates an Excel (.xlsx) import template containing only the mandatory columns
 * with clean headers (no '*' suffix) and dropdown validations, plus an "Instructions" sheet.
 */
@Service
@RequiredArgsConstructor
public class ResourceImportTemplateService {

	private final DepartmentRepository departmentRepo;

	public record TemplateColumn(String header, boolean required, String description, String format, String example,
			List<String> choices) {
	}

	private static final List<TemplateColumn> INTERNAL_COLUMNS = List.of(
			req("Name", "Full name (first word = First Name, remaining words = Last Name).", "Text", "Aarav Sharma", List.of()),
			req("Email", "Unique work email per company.", "Valid work email", "aarav.sharma@rudhrainfosolutions.com", List.of()),
			req("Role", "Role / Designation (maps to Department & Job Title). New departments are created automatically.",
					"Text", "Software Engineer", List.of()),
			req("Status", "Current resource status.", "Billable, Bench, Bench Shadow, Internal", "Bench",
					List.of("Billable", "Bench", "Bench Shadow", "Internal")),
			req("Experience", "Total years of experience (whole number >= 0).", "Number (e.g. 4)", "4", List.of()),
			req("Phone Number", "Primary contact phone number.", "10-digit mobile or with country code", "9876543210", List.of()),
			req("Resume Path",
					"Local file path on server/machine or direct http(s) URL (.pdf, .doc, .docx, max 10 MB).",
					"File path or URL", "C:\\Resumes\\Aarav_Sharma.pdf", List.of()));

	private static final List<TemplateColumn> EXTERNAL_COLUMNS = List.of(
			req("Name", "Full name (first word = First Name, remaining words = Last Name).", "Text", "Priya Nair", List.of()),
			req("Email", "Unique email per company.", "Valid email", "priya.nair@example.com", List.of()),
			req("Role", "Candidate primary role / specialization.", "Text", "Veeva Vault Consultant", List.of()),
			req("Status", "Candidate availability status.", "Allocated, Engaged, Not Allocated, Billable, Bench", "Not Allocated",
					List.of("Allocated", "Engaged", "Not Allocated")),
			req("Experience", "Total years of experience (whole number >= 0).", "Number (e.g. 5)", "5", List.of()),
			req("Phone Number", "Candidate phone number (max 13 chars).", "10-digit mobile", "9876543210", List.of()),
			req("Resume Path",
					"Local file path on server/machine or direct http(s) URL (.pdf, .doc, .docx, max 10 MB).",
					"File path or URL", "C:\\Resumes\\Priya_Nair.docx", List.of()));

	private static TemplateColumn req(String header, String desc, String format, String example, List<String> choices) {
		return new TemplateColumn(header, true, desc, format, example, choices);
	}

	@Transactional(readOnly = true)
	public byte[] generateTemplate(String type, Long companyId) {
		boolean external = "external".equalsIgnoreCase(type);
		List<TemplateColumn> columns = external ? EXTERNAL_COLUMNS : INTERNAL_COLUMNS;
		String sheetName = external ? "Candidates" : "Employees";

		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			CellStyle requiredStyle = headerStyle(workbook, IndexedColors.LIGHT_ORANGE, false);
			CellStyle textStyle = workbook.createCellStyle();
			textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));

			Sheet dataSheet = workbook.createSheet(sheetName);
			Row headerRow = dataSheet.createRow(0);
			headerRow.setHeightInPoints(22);

			DataValidationHelper validation = dataSheet.getDataValidationHelper();
			for (int i = 0; i < columns.size(); i++) {
				TemplateColumn col = columns.get(i);
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(col.header());
				cell.setCellStyle(requiredStyle);
				dataSheet.setColumnWidth(i, Math.max(20, col.header().length() + 8) * 256);
				dataSheet.setDefaultColumnStyle(i, textStyle);

				if (!col.choices().isEmpty()) {
					DataValidation dv = validation.createValidation(
							validation.createExplicitListConstraint(col.choices().toArray(String[]::new)),
							new CellRangeAddressList(1, 1000, i, i));
					dv.setShowErrorBox(true);
					dataSheet.addValidationData(dv);
				}
			}
			dataSheet.createFreezePane(0, 1);

			writeInstructions(workbook.createSheet("Instructions"), sheetName, columns, companyId, workbook);
			workbook.write(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Could not generate RMS import template", e);
		}
	}

	private void writeInstructions(Sheet sheet, String sheetName, List<TemplateColumn> columns, Long companyId,
			Workbook workbook) {
		CellStyle bold = headerStyle(workbook, IndexedColors.GREY_25_PERCENT, true);
		CellStyle wrap = workbook.createCellStyle();
		wrap.setWrapText(true);
		wrap.setVerticalAlignment(VerticalAlignment.TOP);

		int r = 0;
		r = singleCellRow(sheet, r, bold, "How to fill the " + sheetName + " sheet");
		for (String tip : List.of(
				"Fill one resource per row on the '" + sheetName + "' sheet, starting at row 2.",
				"All 7 columns in this template are mandatory: Name, Email, Role, Status, Experience, Phone Number, Resume Path.",
				"Resume Path: Enter a local file path accessible to the backend (e.g. C:\\Resumes\\john.pdf or Downloads\\resume.docx) or an http(s) URL (.pdf, .doc, .docx up to 10 MB).",
				"Role: Maps to Department and Designation/Job Title. If the department does not exist for the company, it is created automatically.")) {
			r = singleCellRow(sheet, r, wrap, "- " + tip);
		}

		r++;
		r = multiCellRow(sheet, r, bold, "Column", "Required", "What to enter", "Format / Allowed values", "Example");
		for (TemplateColumn col : columns) {
			r = multiCellRow(sheet, r, wrap, col.header(), col.required() ? "Yes (Mandatory)" : "No (Optional)",
					col.description(), col.format(), col.example());
		}

		if (companyId != null) {
			List<Department> depts = departmentRepo.findAllByCompanyId(companyId);
			if (!depts.isEmpty()) {
				r++;
				r = singleCellRow(sheet, r, bold, "Existing Roles / Departments for selected company");
				r = multiCellRow(sheet, r, bold, "Column", "Existing Value", "", "", "");
				for (Department d : depts) {
					r = multiCellRow(sheet, r, wrap, "Role", d.getDepartmentName(), "", "", "");
				}
			}
		}

		int[] widths = { 22, 18, 56, 36, 32 };
		for (int i = 0; i < widths.length; i++) {
			sheet.setColumnWidth(i, widths[i] * 256);
		}
	}

	private static int singleCellRow(Sheet sheet, int r, CellStyle style, String text) {
		Cell cell = sheet.createRow(r).createCell(0);
		cell.setCellValue(text);
		cell.setCellStyle(style);
		return r + 1;
	}

	private static int multiCellRow(Sheet sheet, int r, CellStyle style, String... values) {
		Row row = sheet.createRow(r);
		for (int i = 0; i < values.length; i++) {
			Cell cell = row.createCell(i);
			cell.setCellValue(values[i]);
			cell.setCellStyle(style);
		}
		return r + 1;
	}

	private static CellStyle headerStyle(Workbook workbook, IndexedColors color, boolean wrap) {
		CellStyle style = workbook.createCellStyle();
		Font font = workbook.createFont();
		font.setBold(true);
		style.setFont(font);
		style.setFillForegroundColor(color.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		style.setWrapText(wrap);
		style.setVerticalAlignment(VerticalAlignment.CENTER);
		return style;
	}
}
