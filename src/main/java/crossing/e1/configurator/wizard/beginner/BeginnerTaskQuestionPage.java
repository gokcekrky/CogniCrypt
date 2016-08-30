package crossing.e1.configurator.wizard.beginner;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import crossing.e1.configurator.beginer.question.Answer;
import crossing.e1.configurator.beginer.question.Question;
import crossing.e1.configurator.tasks.Task;
import crossing.e1.configurator.utilities.Labels;
import crossing.e1.featuremodel.clafer.ClaferModel;

public class BeginnerTaskQuestionPage extends WizardPage {

	private final Question quest;
	private Entry<Question, Answer> selection = new AbstractMap.SimpleEntry<Question, Answer>(null, null);

	public BeginnerTaskQuestionPage(final Question quest, final Task task) {
		super("Display Questions");
		setTitle("Configuring Selected Task: " + task.getDescription());
		setDescription(Labels.DESCRIPTION_VALUE_SELECTION_PAGE);
		this.quest = quest;
	}

	@Override
	public boolean canFlipToNextPage() {
		return isPageComplete();
	}

	@Override
	public void createControl(final Composite parent) {
		final Composite container = new Composite(parent, SWT.NONE);
		container.setBounds(10, 10, 450, 200);
		final GridLayout layout = new GridLayout();
		container.setLayout(layout);

		createQuestionControl(container, this.quest);
		layout.numColumns = 1;
		setControl(container);
	}

	public void createQuestionControl(final Composite parent, final Question question) {

		final List<Answer> answers = question.getAnswers();
		final Composite container = getPanel(parent);
		final Label label = new Label(container, SWT.CENTER);
		label.setText(question.getQuestionText());
		switch (question.getElement()) {
			case combo:
				final ComboViewer comboViewer = new ComboViewer(container, SWT.DROP_DOWN | SWT.READ_ONLY);
				comboViewer.setContentProvider(ArrayContentProvider.getInstance());
				comboViewer.setInput(answers);

				comboViewer.addSelectionChangedListener(arg0 -> {
					final IStructuredSelection selection = (IStructuredSelection) comboViewer.getSelection();
					BeginnerTaskQuestionPage.this.selection = new AbstractMap.SimpleEntry<>(question, (Answer) selection.getFirstElement());
					//BeginnerTaskQuestionPage.this.selection.put(question, (Answer) selection.getFirstElement());
				});

				comboViewer.setSelection(new StructuredSelection(question.getDefaultAnswer()));
				break;

			case text:
				final Text inputField = new Text(container, SWT.BORDER);
				inputField.setSize(240, inputField.getSize().y);
				inputField.addModifyListener(e -> {
					final Answer a = question.getDefaultAnswer();
					final String cleanedInput = inputField.getText().replaceAll("(?=[]\\[+&|!(){}^\"~*?:\\\\-])", "\\\\");

					a.setValue(cleanedInput);
					a.getCodeDependencies().get(0).setValue(cleanedInput);
					BeginnerTaskQuestionPage.this.selection = new AbstractMap.SimpleEntry<>(question, a);
					//BeginnerTaskQuestionPage.this.selection.put(question, a);
				});

			default:
				break;
		}

	}

	@Override
	public boolean equals(final Object obj) {
		if (!(obj instanceof BeginnerTaskQuestionPage)) {
			return false;
		}
		final BeginnerTaskQuestionPage cmp = (BeginnerTaskQuestionPage) obj;
		return this.quest.equals(cmp.quest);
	}

	public Entry<Question, Answer> getMap() {
		return this.selection;
	}

	private Composite getPanel(final Composite parent) {
		final Composite titledPanel = new Composite(parent, SWT.NONE);
		final Font boldFont = new Font(titledPanel.getDisplay(), new FontData("Arial", 9, SWT.BOLD));
		titledPanel.setFont(boldFont);
		final GridLayout layout2 = new GridLayout();

		layout2.numColumns = 4;
		titledPanel.setLayout(layout2);

		return titledPanel;
	}

	@Override
	public IWizardPage getPreviousPage() {
		final IWizardPage prev = super.getPreviousPage();
		if (prev != null && prev instanceof BeginnerTaskQuestionPage) {
			return getWizard().getPreviousPage(this);
		}
		return prev;
	}

	public synchronized Entry<Question, Answer> getSelection() {
		return this.selection;
	}

	public void setMap(final HashMap<Question, Answer> hashMap, final ClaferModel model) {

		// userOptions = new HashMap<ArrayList<AstConcreteClafer>,
		// ArrayList<Integer>>();
		// ArrayList<Integer> values = null;
		// ArrayList<AstConcreteClafer> keys = null; // new
		// // ArrayList<AstConcreteClafer>();
		// for (AstConcreteClafer clafer : PropertiesMapperUtil.getPropertyLabels()
		// .keySet()) {
		// values = new ArrayList<Integer>();
		// keys = new ArrayList<AstConcreteClafer>();
		// for (AstConcreteClafer claf : PropertiesMapperUtil.getPropertyLabels()
		// .get(clafer)) {
		// HashMap<HashMap<String, String>, List<String>> qutionare = quest
		// .getQutionare();
		// for (HashMap<String, String> val : qutionare.keySet()) {
		// String st1 = val.get(val.keySet().toArray()[0]);
		// String st2 = claf.getName();
		// if (st2.contains(st1)
		// // && map.containsKey(val .get(val.keySet().toArray()[0]))
		// ) {
		// keys.add(clafer);
		// keys.add(claf);
		// values.add(4);
		// values.add(map.get(val.get(val.keySet().toArray()[0])));
		// }
		// }
		// }
		// }
		// if (keys != null && values != null)
		// userOptions.put(keys, values);
		//
		// for (ArrayList<AstConcreteClafer> x : userOptions.keySet()) {
		// System.out.println(x.toString());
		// }
	}

}
