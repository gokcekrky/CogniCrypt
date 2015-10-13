package crossing.e1.featuremodel.clafer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.clafer.ast.AstConcreteClafer;

import crossing.e1.configurator.ReadConfig;
import crossing.e1.featuremodel.clafer.ClaferModel;
import crossing.e1.featuremodel.clafer.InstanceGenerator;
import crossing.e1.featuremodel.clafer.StringLabelMapper;
import crossing.e1.xml.export.PublishToXML;

/**
 * @author Ram
 *
 */

public class TestCases {

	public static void main(String[] args) {
		TestCases test = new TestCases();
		String path = new ReadConfig().getPath("claferPath");
		ClaferModel model = new ClaferModel(path);

		// InstanceGenerator inst = test.getInstance(model);
		// test.displayInstances(inst);
		// test.displayConstraints(model);
		// test.displaySuperClafers(model);
		test.displayTasks(model);
		test.displayProperties(model);

	}

	/**
	 * @param model
	 */
	private void displayProperties(ClaferModel model) {

		System.out.println("----- Listing Propertie -----");

		System.out.println("Task Name "
				+ StringLabelMapper.getTaskLabels().keySet().toArray()[0]
						.toString() + "\nProperties are");
		for (AstConcreteClafer key : StringLabelMapper.getPropertyLabels()
				.keySet()) {
			System.out.println(key + " => "
					+ StringLabelMapper.getPropertyLabels().get(key));

		}

	}

	/**
	 * @param model
	 */
	private void displayTasks(ClaferModel model) {
		model.getTaskList(model.getModel());
		System.out.println("----- Listing Tasks -----");
		for (String inst : StringLabelMapper.getTaskLabels().keySet()) {

			System.out.println(inst + " => "
					+ StringLabelMapper.getTaskLabels().get(inst));
		}
		model.createClaferConstraintMap(StringLabelMapper.getTaskLabels().get(
				StringLabelMapper.getTaskLabels().keySet().toArray()[0]
						.toString()));

	}

	private void displaySuperClafers(ClaferModel model) {

		System.out.println("--Testing displaySuperClafers Method--");
		Map<String, AstConcreteClafer> taskList = new HashMap<String, AstConcreteClafer>();
		taskList = model.getTaskList(model.getModel());
		for (String key : taskList.keySet()) {
			System.out.println("VALUE : " + taskList.get(key));
		}

	}

	private void displayConstraints(ClaferModel model) {
		Map<String, AstConcreteClafer> constraints = model
				.getConstraintClafers();
		System.out.println("--Testing Constrainable properties--");
		for (String key : constraints.keySet()) {
			System.out.println("VALUE : " + constraints.get(key));
		}

	}

	Map<ArrayList<AstConcreteClafer>, ArrayList<Integer>> getMap() {
		Map<ArrayList<AstConcreteClafer>, ArrayList<Integer>> filters = new HashMap<ArrayList<AstConcreteClafer>, ArrayList<Integer>>();
		// filters.put(null, {3});
		// filters.put(null, {256});
		return filters;
	}

	void displayInstances(InstanceGenerator instance) {
		System.out.println("----Diplaying instances----");
		for (String b : instance.getInstances().keySet())
			System.out.println(new PublishToXML().displayInstanceValues(instance
					.getInstances().get(b), ""));
	}

	InstanceGenerator getInstance(ClaferModel model) {
		System.out.println("-- Testing instance Generator method--");
		InstanceGenerator instance = new InstanceGenerator(model);
		;
		instance.generateInstances(getMap(),false);
		System.out.println("There are " + instance.getNoOfInstances()
				+ " instances");
		return instance;
		// instance.displayInstances();
	}

}