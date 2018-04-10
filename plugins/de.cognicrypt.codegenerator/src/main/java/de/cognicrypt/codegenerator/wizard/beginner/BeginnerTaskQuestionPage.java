package de.cognicrypt.codegenerator.wizard.beginner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
//import org.eclipse.swt.custom.StyleRange;
//import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import de.cognicrypt.codegenerator.Activator;
import de.cognicrypt.codegenerator.question.Answer;
import de.cognicrypt.codegenerator.question.CodeDependency;
import de.cognicrypt.codegenerator.question.Page;
import de.cognicrypt.codegenerator.question.Question;
import de.cognicrypt.codegenerator.tasks.Task;
import de.cognicrypt.core.Constants;
import de.cognicrypt.core.Constants.GUIElements;

public class BeginnerTaskQuestionPage extends WizardPage {

	private final Question quest;
	private final Task task;
	private final Page page;

	private boolean finish = false;
	private BeginnerModeQuestionnaire beginnerModeQuestionnaire;
	private final HashMap<Question, Answer> selectionMap = new HashMap<>();
	private List<String> selectionValues;
	private Text note;
	private Composite container;

	public int getCurrentPageID() {
		return this.page.getId();
	}

	/**
	 * construct a page containing an element other than itemselection
	 *
	 * @param quest
	 *        question that will be displayed on the page
	 * @param task
	 *        task for which the page is created
	 */
	public BeginnerTaskQuestionPage(final Question quest, final Task task) {
		this(quest, task, null);
	}

	/**
	 * construct a page containing a single question
	 *
	 * @param quest
	 *        question that will be displayed on the page
	 * @param task
	 *        task for which the page is created
	 * @param selectionValues
	 *        list of selectable strings if element type of quest is itemselection, null otherwise
	 */
	public BeginnerTaskQuestionPage(final Question quest, final Task task, final List<String> selectionValues) {
		super("Display Questions");
		setTitle("Configuring Selected Task: " + task.getDescription());
		setDescription(Constants.DESCRIPTION_VALUE_SELECTION_PAGE);
		this.quest = quest;
		this.task = task;
		this.selectionValues = selectionValues;

		// This variable needs to be initialized.
		this.page = null;
	}

	/**
	 *
	 * @param page
	 *        page contains the questions that need to be displayed.
	 * @param task
	 *        task for which the page is created
	 * @param selectionValues
	 *        The call to this constructor needs to have this extra parameter for itemselection. list of selectable strings if element type of quest is itemselection, null
	 *        otherwise
	 */
	public BeginnerTaskQuestionPage(final Page page, final Task task, final List<String> selectionValues) {
		super("Display Questions");
		setTitle("Configuring Selected Task: " + task.getDescription());
		setDescription(Constants.DESCRIPTION_VALUE_SELECTION_PAGE);
		this.page = page;
		this.task = task;
		this.selectionValues = selectionValues;

		//This variable needs to be initialized.
		this.quest = null;
	}

	/**
	 *
	 * @param page
	 * @param task
	 * @param beginnerModeQuestionnaire
	 *        Updated this parameter in the constructor to accept the questionnaire instead of all the questions.
	 * @param selectionValues
	 */
	public BeginnerTaskQuestionPage(final Page page, final Task task, final BeginnerModeQuestionnaire beginnerModeQuestionnaire, final List<String> selectionValues) {
		super("Display Questions");
		setTitle("Configuring Selected Task: " + task.getDescription());
		setDescription(Constants.DESCRIPTION_VALUE_SELECTION_PAGE);
		this.beginnerModeQuestionnaire = beginnerModeQuestionnaire;
		this.quest = null;
		this.page = page;
		this.task = task;
		this.selectionValues = selectionValues;
	}

	/**
	 *
	 * @param beginnerModeQuestionnaire
	 *        Updated this parameter in the constructor to accept the questionnaire instead of all the questions.
	 * @param quest
	 * @param task
	 */
	public BeginnerTaskQuestionPage(final BeginnerModeQuestionnaire beginnerModeQuestionnaire, final Question quest, final Task task) {
		super("Display Questions");
		setTitle("Configuring Selected Task: " + task.getDescription());
		setDescription(Constants.DESCRIPTION_VALUE_SELECTION_PAGE);
		this.beginnerModeQuestionnaire = beginnerModeQuestionnaire;
		this.quest = quest;
		this.page = null;
		this.task = task;
	}

	@Override
	public boolean isPageComplete() {
		for (final Question question : this.page.getContent()) {
			final Answer answer = question.getEnteredAnswer();
			if (answer == null || answer.getValue().isEmpty()) {
				return false;
			}
			if (Arrays.asList((new GUIElements[] { GUIElements.button, GUIElements.itemselection, GUIElements.radio, GUIElements.scale })).contains(question.getElement())) {
				return this.finish;
			}
		}
		return true;
	}

	public String getHelpId(final Page page) {
		return "de.cognicrypt.codegenerator." + page.getHelpID();
	}

	@Override
	public void createControl(final Composite parent) {
		this.container = new Composite(parent, SWT.NONE);
		this.container.setBounds(10, 10, 450, 200);
		// Updated the number of columns to order the questions vertically.
		final GridLayout layout = new GridLayout(1, false);

		// To display the Help view after clicking the help icon
		if (!this.page.getHelpID().isEmpty()) {
			PlatformUI.getWorkbench().getHelpSystem().setHelp(this.container, getHelpId(this.page));
		}

		this.container.setLayout(layout);
		// If legacy JSON files are in effect.
		if (this.page == null) {
			createQuestionControl(this.container, this.quest);
			Activator.getDefault().logError("Outdated json file is used for task " + this.task.getDescription() + ". Please update.");
		} else {
			// loop through the questions that are to be displayed on the page.
			for (final Question question : this.page.getContent()) {
				createQuestionControl(this.container, question);
			}
			//setting focus to the first field on the page
			this.container.getChildren()[0].setFocus();
		}
		setControl(this.container);
	}

	private void createQuestionControl(final Composite parent, final Question question) {

		final List<Answer> answers = question.getAnswers();
		final Composite container = getPanel(parent);
		final Label label = new Label(container, SWT.TOP);
		label.setText(question.getQuestionText());
		switch (question.getElement()) {
			case combo:
				final ComboViewer comboViewer = new ComboViewer(container, SWT.DROP_DOWN | SWT.READ_ONLY);
				comboViewer.setContentProvider(ArrayContentProvider.getInstance());
				comboViewer.setInput(answers);

				comboViewer.addSelectionChangedListener(selectedElement -> {
					final IStructuredSelection selection = (IStructuredSelection) comboViewer.getSelection();
					BeginnerTaskQuestionPage.this.selectionMap.put(question, (Answer) selection.getFirstElement());
					question.setEnteredAnswer((Answer) selection.getFirstElement());
				});
				new Label(parent, SWT.NONE);
				//added description for questions
				if (!question.getNote().isEmpty()) {
					createNote(parent, question);
				}
				this.finish = true;
				BeginnerTaskQuestionPage.this.setPageComplete(this.finish);
				if (question.getEnteredAnswer() != null) {
					comboViewer.setSelection(new StructuredSelection(question.getEnteredAnswer()));
				} else {
					comboViewer.setSelection(new StructuredSelection(question.getDefaultAnswer()));
				}
				break;
			case radio:
				final Button[] radioButton = new Button[answers.size()];
				for (int i = 0; i < answers.size(); i++) {
					final int count = i;
					final String ans = answers.get(i).getValue();
					radioButton[i] = new Button(container, SWT.RADIO);
					radioButton[i].setText(ans);
					new Label(container, SWT.NONE);
					radioButton[i].addSelectionListener(new SelectionAdapter() {

						@Override
						public void widgetSelected(final SelectionEvent e) {
							BeginnerTaskQuestionPage.this.selectionMap.put(question, answers.get(count));
							question.setEnteredAnswer(answers.get(count));
						}
					});
				}
				if (!question.getNote().isEmpty()) {
					createNote(parent, question);
				}
				Answer evalAnswer = question.getEnteredAnswer();
				if (evalAnswer == null) {
					evalAnswer = question.getDefaultAnswer();
				}
				for (int i = 0; i < answers.size(); i++) {
					if (radioButton[i].getText().equals(evalAnswer.getValue())) {
						radioButton[i].setSelection(true);
						BeginnerTaskQuestionPage.this.selectionMap.put(question, evalAnswer);
						question.setEnteredAnswer(evalAnswer);
					}
				}

				BeginnerTaskQuestionPage.this.setPageComplete(this.finish = true);
				break;

			case scale:

				final Scale scale = new Scale(container, SWT.HORIZONTAL);
				scale.setMaximum((answers.size()) - 1);
				scale.setMinimum(0);
				scale.setPageIncrement(1);

				for (int i = 0; i < answers.size(); i++) {
					scale.addSelectionListener(new SelectionAdapter() {

						@Override
						public void widgetSelected(final SelectionEvent selectionEvent) {
							final int selectionNum = scale.getSelection();
							scale.setToolTipText(answers.get(selectionNum).getValue());
							BeginnerTaskQuestionPage.this.selectionMap.put(question, answers.get(selectionNum));
							question.setEnteredAnswer(answers.get(selectionNum));
						}
					});
				}

				if (question.getEnteredAnswer() != null) {
					for (int i = 0; i < answers.size(); i++) {
						if (answers.get(i).getValue().equals(question.getEnteredAnswer().getValue())) {
							scale.setSelection(i);
							scale.setToolTipText(answers.get(i).getValue());
							BeginnerTaskQuestionPage.this.selectionMap.put(question, answers.get(i));
							question.setEnteredAnswer(answers.get(i));
						}
					}
				} else {
					for (int i = 0; i < answers.size(); i++) {
						if (answers.get(i).getValue().equals(question.getDefaultAnswer().getValue())) {
							scale.setSelection(i);
							scale.setToolTipText(answers.get(i).getValue());
							BeginnerTaskQuestionPage.this.selectionMap.put(question, answers.get(i));
							question.setEnteredAnswer(answers.get(i));
						}
					}
				}

				BeginnerTaskQuestionPage.this.setPageComplete(this.finish = true);
				break;

			case text:
				final Text inputField = new Text(container, SWT.BORDER);
				inputField.setSize(240, inputField.getSize().y);
				inputField.setToolTipText(question.getTooltip());

				if (question.getEnteredAnswer() != null) {
					final Answer a = question.getEnteredAnswer();
					inputField.setText(a.getValue());
					a.getCodeDependencies().get(0).setValue(a.getValue());
					this.finish = !inputField.getText().isEmpty();
					BeginnerTaskQuestionPage.this.setPageComplete(this.finish);
				}

				inputField.addModifyListener(e -> {
					final Answer a = question.getDefaultAnswer();
					final String cleanedInput = inputField.getText().replaceAll("(?=[]\\[+&|!(){}^\"~*?\\\\-])", "\\\\");
					a.setValue(cleanedInput);
					if (a.getCodeDependencies() != null) {
						for (CodeDependency codeDep : a.getCodeDependencies()) {
							codeDep.setValue(cleanedInput);
						}
					}
					this.finish = !cleanedInput.isEmpty();
					BeginnerTaskQuestionPage.this.selectionMap.put(question, a);
					question.setEnteredAnswer(a);
					BeginnerTaskQuestionPage.this.setPageComplete(isPageComplete());
				});
				if (question.getDefaultAnswer().getCodeDependencies() != null) {
					inputField.setText(question.getDefaultAnswer().getCodeDependencies().get(0).getValue());
				}
				break;

			case itemselection:
				new Label(container, SWT.NONE);
				new Label(container, SWT.NONE);
				new Label(container, SWT.NONE);

				final org.eclipse.swt.widgets.List itemList = new org.eclipse.swt.widgets.List(container, SWT.LEFT | SWT.MULTI | SWT.V_SCROLL);

				final Composite composite = new Composite(container, SWT.CENTER);
				GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, false);
				gridData.horizontalSpan = 2;
				composite.setLayoutData(gridData);
				composite.setLayout(new GridLayout(1, false));

				gridData = new GridData(SWT.CENTER, SWT.CENTER, true, false);

				final Button moveRightButton = new Button(composite, SWT.TOP);
				moveRightButton.setLayoutData(gridData);

				final Button moveLeftButton = new Button(composite, SWT.BOTTOM);

				moveRightButton.setText("-Select->");
				moveLeftButton.setText("<-Deselect-");
				moveRightButton.setEnabled(false);
				moveLeftButton.setEnabled(false);

				final org.eclipse.swt.widgets.List selectedItemList = new org.eclipse.swt.widgets.List(container, SWT.RIGHT | SWT.MULTI | SWT.V_SCROLL);
				selectedItemList.setEnabled(false);

				for (final String value : this.selectionValues) {
					itemList.add(value);
					selectedItemList.add("                                                                           ");
				}

				itemList.addSelectionListener(new SelectionListener() {

					@Override
					public void widgetDefaultSelected(final SelectionEvent e) {
						return;
					}

					@Override
					public void widgetSelected(final SelectionEvent e) {
						if (e.getSource() instanceof org.eclipse.swt.widgets.List) {
							final org.eclipse.swt.widgets.List sel = (org.eclipse.swt.widgets.List) e.getSource();
							moveRightButton.setEnabled(sel.getSelectionCount() > 0);
						}

					}
				});

				selectedItemList.addSelectionListener(new SelectionListener() {

					@Override
					public void widgetDefaultSelected(final SelectionEvent e) {
						return;
					}

					@Override
					public void widgetSelected(final SelectionEvent e) {
						if (e.getSource() instanceof org.eclipse.swt.widgets.List) {
							final org.eclipse.swt.widgets.List sel = (org.eclipse.swt.widgets.List) e.getSource();
							moveLeftButton.setEnabled(sel.getSelectionCount() > 0);
						}

					}
				});

				moveRightButton.addSelectionListener(new SelectionListener() {

					@Override
					public void widgetDefaultSelected(final SelectionEvent e) {
						return;
					}

					@Override
					public void widgetSelected(final SelectionEvent e) {
						if (e.getSource() instanceof Button && (((Button) e.getSource()).getStyle() & SWT.NONE) == SWT.NONE) {
							final String[] sel = itemList.getSelection();
							Answer ans = null;
							// Since this part is for the item selection, there will only be a single entry for this page.
							for (final Entry<Question, Answer> selectionEntry : BeginnerTaskQuestionPage.this.selectionMap.entrySet()) {
								ans = selectionEntry.getValue();
							}
							final StringBuilder checkedElement = new StringBuilder();
							if (ans == null) {
								ans = new Answer();
								// TODO Why is this -1? Does it still make sense after having introduced multiple questions per page?
								ans.setNextID(-1);
							} else {
								checkedElement.append(ans.getValue());
							}

							if (selectedItemList.getItemCount() > 0 && selectedItemList.getItem(0).trim().isEmpty()) {
								selectedItemList.removeAll();
								selectedItemList.setEnabled(true);
							}

							for (final String item : sel) {
								selectedItemList.add(item);
								itemList.remove(item);
								checkedElement.append(item);
								checkedElement.append(";");
							}
							ans.setValue(checkedElement.toString());
							BeginnerTaskQuestionPage.this.finish = ans.getValue().contains(";");
							BeginnerTaskQuestionPage.this.setPageComplete(BeginnerTaskQuestionPage.this.finish);
							BeginnerTaskQuestionPage.this.selectionMap.put(question, ans);
							moveRightButton.setEnabled(false);
						}
					}
				});

				moveLeftButton.addSelectionListener(new SelectionListener() {

					@Override
					public void widgetDefaultSelected(final SelectionEvent e) {
						return;
					}

					@Override
					public void widgetSelected(final SelectionEvent e) {
						if (e.getSource() instanceof Button && (((Button) e.getSource()).getStyle() & SWT.NONE) == SWT.NONE) {
							final String[] sel = selectedItemList.getSelection();

							Answer ans = null;
							// Since this part is for the item selection, there will only be a single entry for this page.
							for (final Entry<Question, Answer> selectionEntry : BeginnerTaskQuestionPage.this.selectionMap.entrySet()) {
								ans = selectionEntry.getValue();
							}
							if (ans == null) {
								ans = new Answer();
								ans.setNextID(-1);
							}
							String checkedElement = ans.getValue();

							for (final String item : sel) {
								selectedItemList.remove(item);
								if (!item.trim().isEmpty()) {
									itemList.add(item);
									checkedElement = checkedElement.replace(item + ";", "");
								}
							}
							ans.setValue(checkedElement);
							BeginnerTaskQuestionPage.this.finish = ans.getValue().contains(";");
							BeginnerTaskQuestionPage.this.setPageComplete(BeginnerTaskQuestionPage.this.finish);
							BeginnerTaskQuestionPage.this.selectionMap.put(question, ans);
							moveLeftButton.setEnabled(false);
						}
					}
				});
				break;

			case button:
				for (int i = 0; i < 3; i++) {
					new Label(container, SWT.NULL);
				}

				final Composite comp = new Composite(container, SWT.NONE);
				final GridLayout grid = new GridLayout(2, false);
				grid.marginWidth = 0;
				comp.setLayout(grid);

				final Button methodButton = new Button(comp, SWT.PUSH);
				final ArrayList<String> methodArrayList = question.getMethod();
				final String buttonName = methodArrayList.get(0);
				final String className = methodArrayList.get(1);
				final String methodName = methodArrayList.get(2);

				methodButton.setText(buttonName);
				final Label feedbackLabel = new Label(comp, SWT.NONE);

				Object classObj = null;
				Method method = null;
				Object[] paramArray = null;

				try {
					final Class<?> c = Class.forName(className);
					final Method[] methods = c.getMethods();

					for (final Method m : methods) {
						if (m.getName().equals(methodName)) {
							method = m;
							break;
						}
					}

					final Class<?>[] paramTypes = method.getParameterTypes();
					final ArrayList<Object> paramObjList = new ArrayList<>();
					final ArrayList<Integer> methodParamIds = question.getMethodParamIds();
					String value = null;

					for (int i = 0; i < methodParamIds.size(); i++) {
						// Updated this code to pull just specific questions from the questionnaire.
						// getQuestionByID may return null in case of a bad json file. Updated the catch block.
						value = this.beginnerModeQuestionnaire.getQuestionByID(methodParamIds.get(i)).getAnswers().get(0).getValue();

						if (!paramTypes[i].getName().equals("int")) {
							paramObjList.add(paramTypes[i].cast(value));
						} else {
							// updated this code to reuse the value variable instead of the earlier line of code.
							paramObjList.add(Integer.parseInt(value));
						}
					}

					classObj = c.newInstance();
					paramArray = paramObjList.toArray();

				} catch (NullPointerException | SecurityException | ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InstantiationException e) {
					Activator.getDefault().logError(e);
				}

				final Method invokeMethod = method;
				final Object invokeClassObj = classObj;
				final Object[] invokeParamArray = paramArray;

				methodButton.addSelectionListener(new SelectionAdapter() {

					@Override
					public void widgetSelected(final SelectionEvent e) {
						try {

							final Object[] resObjArray = (Object[]) invokeMethod.invoke(invokeClassObj, invokeParamArray);
							final String[] resStringArray = Arrays.copyOf(resObjArray, resObjArray.length, String[].class);
							final boolean methodResult = Boolean.parseBoolean(resStringArray[0]);
							final String feedbackString = resStringArray[1];

							if (methodResult) {
								question.setEnteredAnswer(question.getAnswers().get(0));
							} else {
								question.setEnteredAnswer(question.getAnswers().get(1));
							}

							feedbackLabel.setText(feedbackString);
							feedbackLabel.getParent().pack();
							methodButton.setEnabled(false);

							BeginnerTaskQuestionPage.this.finish = methodResult;
							BeginnerTaskQuestionPage.this.setPageComplete(BeginnerTaskQuestionPage.this.finish);
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e1) {
							Activator.getDefault().logError(e1);
						}

					}
				});

				this.finish = true;
				BeginnerTaskQuestionPage.this.setPageComplete(this.finish);

				break;

			default:
				break;
		}
	}

	private void createNote(final Composite parent, final Question question) {
		final Group notePanel = new Group(parent, SWT.NONE);
		notePanel.setText("Note:");
		final Font boldFont = new Font(notePanel.getDisplay(), new FontData(Constants.ARIAL, 10, SWT.BOLD));
		notePanel.setFont(boldFont);

		this.note = new Text(notePanel, SWT.MULTI | SWT.WRAP);
		this.note.setLayoutData(new GridData(GridData.FILL_BOTH));
		this.note.setText(question.getNote());
		this.note.setBounds(10, 20, 585, 60);
		this.note.setSize(this.note.computeSize(585, SWT.DEFAULT));
		setControl(notePanel);
		this.note.setEditable(false);
		this.note.setEnabled(true);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof BeginnerTaskQuestionPage)) {
			return false;
		}
		final BeginnerTaskQuestionPage other = (BeginnerTaskQuestionPage) obj;
		if (this.finish != other.finish) {
			return false;
		}
		if (this.quest == null) {
			if (other.quest != null) {
				return false;
			}
		} else if (!this.quest.equals(other.quest)) {
			return false;
		}
		if (this.selectionMap == null) {
			if (other.selectionMap != null) {
				return false;
			}
		} else if (!this.selectionMap.equals(other.selectionMap)) {
			return false;
		}

		if (this.selectionValues == null) {
			if (other.selectionValues != null) {
				return false;
			}
		} else if (!this.selectionValues.equals(other.selectionValues)) {
			return false;
		}
		if (this.page != other.page) {
			return false;
		}
		return true;
	}

	public HashMap<Question, Answer> getMap() {
		return this.selectionMap;
	}

	/**
	 *
	 * @return returns the id of the current page.
	 */
	public int getPageMap() {
		return this.page.getId();
	}

	public int getPageNextID() {
		int nextID = -1;
		if (this.page != null) {
			nextID = this.page.getNextID();
		}
		if (nextID > -2) {
			return nextID;
		} else {
			// in this case there would only be one question on a page, thus only have a single selection.
			for (final Entry<Question, Answer> entry : this.selectionMap.entrySet()) {
				return entry.getValue().getNextID();
			}
		}
		return nextID;
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

	public synchronized HashMap<Question, Answer> getSelection() {
		return this.selectionMap;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (this.finish ? 1231 : 1237);
		result = prime * result + ((this.quest == null) ? 0 : this.quest.hashCode());
		result = prime * result + ((this.page == null) ? 0 : this.quest.hashCode());
		result = prime * result + ((this.selectionMap == null) ? 0 : this.selectionMap.hashCode());
		result = prime * result + ((this.selectionValues == null) ? 0 : this.selectionValues.hashCode());
		return result;
	}

	@Override
	public void setVisible(final boolean visible) {
		super.setVisible(visible);
		if (visible) {
			this.container.setFocus();
		}
	}
}
