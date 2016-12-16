package de.paginagmbh.epubchecker;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.swing.SwingWorker;

import com.adobe.epubcheck.api.EpubCheck;
import com.adobe.epubcheck.api.Report;
import com.adobe.epubcheck.messages.Severity;
import com.adobe.epubcheck.util.Archive;
import com.adobe.epubcheck.util.FeatureEnum;
import com.google.common.io.Files;

import de.paginagmbh.epubchecker.GuiManager.ExpandedSaveMode;



/**
 * Validates EPUB files with EpubCheck
 * in a SwingWorker instance
 * 
 * @author   Tobias Fischer
 * @date     2016-12-14
 */
public class EpubValidator {

	private static GuiManager guiManager = GuiManager.getInstance();
	private final mainGUI gui = guiManager.getCurrentGUI();
	private long timestamp_begin;
	private long timestamp_end;
	private boolean epubcheckResult;
	protected File epubFile = null;
	private Report report = null;
	private String resultMessage = "";
	private final String epubFileExtRegex = "(?i)\\.epub$";
	private boolean expanded = false;
	private File expandedBasedir = null;




	/* ********************************************************************************************************** */

	public EpubValidator() {
		// nothing to do here...
	}




	/* ********************************************************************************************************** */

	public void validate(File file) {
		List<File> files = new ArrayList<File>();
		files.add(file);
		validate(files);
	}

	public void validate(List<File> files) {
		// reset border color
		gui.setBorderStateNormal();

		// If exactly 1 file was dropped
		if(files.size() == 1) { 
			for(int i=0; i<files.size(); i++) {

				File file = files.get(i);

				// EPUB files
				if(file.isFile() && file.getName().toLowerCase().endsWith(".epub")) {
					// internal setters
					this.report = createReport(file);
					this.epubFile = file;

					// set file path in the file-path-input field
					guiManager.setCurrentFile(file);

					// run the validation
					runValidation();


				// expanded EPUB source folders
				} else if(file.isDirectory()) {
					File expectedMimetype = new File(file.getPath(), "mimetype");
					File expectedMetaInf = new File(file.getPath(), "META-INF");

					if(expectedMimetype.exists() && expectedMimetype.isFile()
							&& expectedMetaInf.exists() && expectedMetaInf.isDirectory()) {

						Archive epub = new Archive(file.getPath(), true);

						// #11 create EPUB in temp dir
						File temporaryEpubFile = new File(FileManager.path_TempDir, epub.getEpubName());
						if(FileManager.path_TempDir.exists()) {
							if(temporaryEpubFile.exists()) {
								temporaryEpubFile.delete();
							}
						} else {
							FileManager.path_TempDir.mkdirs();
						}
						epub.createArchive(temporaryEpubFile);

						// internal setters
						this.report = createReport(file);
						this.expanded = true;
						this.expandedBasedir = file.getParentFile();
						this.epubFile = temporaryEpubFile;

						// set basedir path in the file-path-input field
						guiManager.setCurrentFile(file);

						// run the validation
						runValidation();

					} else {
						gui.setBorderStateError();
						gui.clearLog();
						gui.addLogMessage(__("This folder doesn't seem to contain any valid EPUB structure") + ": " + file.getName() + "/");
						gui.addLogMessage("\n\n" + __("There should be at least a folder named 'META-INF' and the 'mimetype' file..."));
					}


				} else {
					gui.setBorderStateError();
					gui.clearLog();
					gui.addLogMessage(__("This isn't an EPUB file") + ": " + file.getName());
				}

			}

			// if multiple files were dropped
		} else {
			gui.setBorderStateError();
			gui.clearLog();
			gui.addLogMessage(__("Sorry, but more than one file can't be validated at the same time!"));
		}
	}




	/* ********************************************************************************************************** */

	private Report createReport(File file) {
		paginaReport report = new paginaReport(file.getName());
		report.info(null, FeatureEnum.TOOL_NAME, "epubcheck");
		report.info(null, FeatureEnum.TOOL_VERSION, EpubCheck.version());
		return report;
	}




	/* ********************************************************************************************************** */

	private void runValidation() {

		// set "begin" timestamp
		timestamp_begin = System.currentTimeMillis();

		// clear and reset TextArea and Table
		gui.clearLog();

		// Print timestamp of current epubcheck
		Calendar cal = Calendar.getInstance();
		cal.setTime( new Date() );
		DateFormat formater = DateFormat.getDateTimeInstance( DateFormat.LONG, DateFormat.LONG );
		gui.addLogMessageToTextLog(formater.format(cal.getTime()) + "\n\n\n");
		gui.addLogMessageToTextLog("---------------------------------------------------\n\n");

		// disable validation button && "save" menuItem
		gui.disableButtonsDuringValidation();

		// set the loading icon and update the statusbar
		gui.getStatusBar().update(FileManager.iconLoading, __("Checking file"));

		// reset border color to normal
		gui.setBorderStateNormal();


		// init SwingWorker
		SwingWorker<Void, Void> validationWorker = new SwingWorker<Void, Void>() {

			@Override
			protected Void doInBackground() throws Exception {

				// run original epubcheck
				EpubCheck epubcheck = new EpubCheck(epubFile, report);
				epubcheckResult = epubcheck.validate();

				return null;
			}


			@Override
			protected void done() {

				// validation finished with warnings or errors
				if(epubcheckResult == false) {

					// set border color to red
					gui.setBorderStateError();

					// add separator in text mode
					gui.addLogMessageToTextLog("\n" + "---------------------------------------------------");


					// warnings AND errors
					if(report.getErrorCount() > 0 && report.getWarningCount() > 0) {
						resultMessage = String.format(__("Check finished with %1$1s warnings and %2$1s errors!"), report.getWarningCount(), report.getErrorCount());

					// only errors
					} else if(report.getErrorCount() > 0) {
						resultMessage = String.format(__("Check finished with %d errors!"), report.getErrorCount());

					// only warnings
					} else if(report.getWarningCount() > 0) {
						// set border color to orange
						gui.setBorderStateWarning();
						resultMessage = String.format(__("Check finished with %d warnings!"), report.getWarningCount());

					// something went wrong
					} else {
						resultMessage = __("Check finished with warnings or errors!");
					}

					// add result message to log
					gui.addLogMessage("\n\n" + resultMessage + "\n");


					// set error counter in mac dock badge
					if(guiManager.getMacApp() != null) {
						if(report.getWarningCount() + report.getErrorCount() > 0) {
							guiManager.getMacApp().setDockIconBadge(new Integer(report.getWarningCount() + report.getErrorCount()).toString());
						} else {
							guiManager.getMacApp().setDockIconBadge("error");
						}
					}


					// #20 save the temporarily created EPUB file if ExpandedSaveMode.ALWAYS is set
					if(expanded) {
						if(guiManager.getExpandedSave() == ExpandedSaveMode.ALWAYS) {
							saveEpubFromExpandedFolder();
						} else if(epubFile.exists()) {
							epubFile.delete();
							gui.addLogMessage(Severity.WARNING, "\n\n" + __("EPUB from source folder wasn't saved because it contains errors or warnings!") + "\n");
						}
					}


				// validation finished without warnings or errors
				} else {

					// set border color to green
					gui.setBorderStateValid();

					// translateLog the output
					resultMessage = __("No errors or warnings detected");
					gui.addLogMessage("\n\n" + resultMessage + "\n");

					// set error counter in mac dock badge
					if(guiManager.getMacApp() != null) {
						guiManager.getMacApp().setDockIconBadge("✓");
					}

					// #20 save the temporarily created EPUB file if ExpandedSaveMode != NEVER is set
					if(expanded && guiManager.getExpandedSave() != ExpandedSaveMode.NEVER) {
						saveEpubFromExpandedFolder();
					}
				}


				// scroll to the end
				gui.scrollToBottom();


				// set "end" timestamp
				timestamp_end = System.currentTimeMillis();

				// calculate the processing duration
				double timestamp_diff = timestamp_end-timestamp_begin;
				DecimalFormat df = new DecimalFormat("0.0#");
				String timestamp_result = df.format(timestamp_diff/1000);

				// remove the loading icon and update the status bar
				gui.getStatusBar().update(null,
						__("Done") + ". "
						+ String.format(__("Validated in %s seconds"), timestamp_result) + ". "
						+ resultMessage);

				// re-enable validation button && "save" menuItem
				gui.enableButtonsAfterValidation();

				// Auto Save logfile if desired
				if(guiManager.getMenuOptionAutoSaveLogfile()) {
					if(expanded && expandedBasedir != null && expandedBasedir.exists()) {
						gui.saveLogfile(new File(expandedBasedir, epubFile.getName().replaceAll(epubFileExtRegex, "_log.txt")));
					} else {
						gui.saveLogfile(new File(epubFile.getAbsolutePath().replaceAll(epubFileExtRegex, "_log.txt")));
					}
				}

				// scroll to the end
				gui.scrollToBottom();
			}
		};

		// execute SwingWorker
		validationWorker.execute();
	}




	/* ********************************************************************************************************** */

	// #11: move temp epub to basedir
	private void saveEpubFromExpandedFolder() {
		if(expanded && epubFile.exists() && guiManager.getExpandedSave() != ExpandedSaveMode.NEVER) {
			if(expanded && expandedBasedir != null && expandedBasedir.exists()) {
				File destEpubFile = new File(expandedBasedir, epubFile.getName().replaceAll(epubFileExtRegex, "") + "__created-with-epubcheck.epub");
				if(destEpubFile.exists()) {
					destEpubFile.delete();
				}
				try {
					// #36: do not use epubFile.renameTo() as it doesn't work for Mac external volumes
					Files.move(epubFile, destEpubFile);
					if(destEpubFile.exists()) {
						gui.addLogMessage("\n\n" + __("EPUB from source folder was successfully saved!") + "\n" + destEpubFile.getAbsolutePath() + "\n");
					} else {
						throw new IOException("(ErrorCode 4)");
					}
				} catch (IOException e) {
					gui.addLogMessage(Severity.ERROR, "\n\n" + __("EPUB from source folder couldn't be saved next to the source folder!") + " (ErrorCode 3)" + "\n" + e.getMessage() + "\n");
				}
			} else {
				gui.addLogMessage(Severity.ERROR, "\n\n" + __("EPUB from source folder couldn't be saved next to the source folder!") + " (ErrorCode 2)" + "\n");
			}
		} else {
			gui.addLogMessage(Severity.ERROR, "\n\n" + __("EPUB from source folder couldn't be saved next to the source folder!") + " (ErrorCode 1)" + "\n");
		}
	}




	/* ********************************************************************************************************** */

	private String __(String s) {
		return GuiManager.getInstance().getCurrentLocalizationObject().getString(s);
	}
}