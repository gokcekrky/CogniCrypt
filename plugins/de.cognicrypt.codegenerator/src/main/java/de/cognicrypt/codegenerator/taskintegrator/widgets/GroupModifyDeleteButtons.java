package de.cognicrypt.codegenerator.taskintegrator.widgets;

import java.util.ArrayList;

import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.MessageBox;

import de.cognicrypt.codegenerator.question.Question;
import de.cognicrypt.codegenerator.taskintegrator.models.ClaferModel;
import de.cognicrypt.codegenerator.taskintegrator.wizard.QuestionDialog;



public class GroupModifyDeleteButtons extends Group {

	/**
	 * Create the composite.
	 * @param parent
	 * @param style
	 */
	int counter=0;
	//private ArrayList<ClaferFeature> claferFeatures;

	public GroupModifyDeleteButtons(Composite parent, Question questionParam) {
		super(parent, SWT.RIGHT_TO_LEFT);
		//setClaferFeatures(claferFeatures);
		RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
		setLayout(rowLayout);
		
		Button btnDelete = new Button(this, SWT.NONE);
		btnDelete.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MessageBox confirmationMessageBox = new MessageBox(getShell(), SWT.ICON_WARNING
		            | SWT.YES | SWT.NO);
				confirmationMessageBox.setMessage("This information will be lost. Do you really want to delete?");
				confirmationMessageBox.setText("Deleting Question");
		        int response = confirmationMessageBox.open();
		        if (response == SWT.YES){
		        	((CompositeToHoldGranularUIElements) btnDelete.getParent().getParent().getParent().getParent()).deleteQuestion(((CompositeGranularUIForHighLevelQuestions)btnDelete.getParent().getParent()).getQuestion());// (1) CompositeGranularUIForClaferFeature, (2) composite inside (3) CompositeToHoldGranularUIElements
		        }
			}
		});
		btnDelete.setLayoutData(new RowData(66, SWT.DEFAULT));
		btnDelete.setText("Delete");
		
		Button btnModify = new Button(this, SWT.NONE);
		btnModify.setLayoutData(new RowData(66, SWT.DEFAULT));
		btnModify.setText("Modify");
		ArrayList<Question> listOfAllQuestions=((CompositeToHoldGranularUIElements) btnModify.getParent().getParent().getParent().getParent()).getListOfAllQuestions();
		ClaferModel claferModel = ((CompositeToHoldGranularUIElements) btnModify.getParent().getParent().getParent().getParent()).getClaferModel();

		QuestionDialog qstnDialog = new QuestionDialog(parent.getShell(), questionParam, claferModel, listOfAllQuestions);
		btnModify.addSelectionListener(new SelectionAdapter(){
			@Override
			public void widgetSelected(SelectionEvent e){
				int response=qstnDialog.open();
				if(response==Window.OK){
					Question modifiedQuestion=qstnDialog.getQuestionDetails();
					((CompositeToHoldGranularUIElements) btnModify.getParent().getParent().getParent().getParent()).modifyHighLevelQuestion(questionParam, modifiedQuestion);
				}
			}
		});

		this.setSize(SWT.DEFAULT, 40);
	}

/*	private void setClaferFeatures(ArrayList<ClaferFeature> claferFeatures) {
		this.claferFeatures = claferFeatures;
	}*/
	
	@Override
	protected void checkSubclass() {
		// Disable the check that prevents subclassing of SWT components
	}
}