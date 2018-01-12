/**
 * 
 */
package de.cognicrypt.codegenerator.taskintegrator.models;

import java.util.ArrayList;

import de.cognicrypt.codegenerator.Constants;
import de.cognicrypt.codegenerator.Constants.FeatureType;

/**
 * @author rajiv
 *
 */
public class ClaferFeature {
	private FeatureType featureType;
	private String featureName;
	private String featureInheritance;
	private ArrayList<FeatureProperty> featureProperties; // <String name, String value>. The names in this collection cannot be repeated.
	private ArrayList<ClaferConstraint> featureConstraints; // each constraint will be generated as a "valid" string in the constraint generator pop up.
	/**
	 * @param featureType
	 * @param featureName
	 * @param featureInheritance
	 * 
	 * 
	 */
	public ClaferFeature(FeatureType featureType, String featureName, String featureInheritance) {
		super();
		this.setFeatureType(featureType);
		this.setFeatureName(featureName);
		this.setFeatureInheritance(featureInheritance);
		this.featureProperties = new ArrayList<FeatureProperty>();
		this.featureConstraints = new ArrayList<ClaferConstraint>();
	}
	/**
	 * @return the featureType
	 */
	public FeatureType getFeatureType() {
		return featureType;
	}
	/**
	 * @param featureType the featureType to set
	 */
	public void setFeatureType(FeatureType featureType) {
		this.featureType = featureType;
	}
	/**
	 * @return the featureName
	 */
	public String getFeatureName() {
		return featureName;
	}
	/**
	 * @param featureName the featureName to set
	 */
	public void setFeatureName(String featureName) {
		this.featureName = featureName;
	}

	public String getFeatureInheritance() {
		return featureInheritance;
	}

	public void setFeatureInheritance(String featureInheritance) {
		this.featureInheritance = featureInheritance;
	}

	/**
	 * @return the properties
	 */
	public ArrayList<FeatureProperty> getfeatureProperties() {
		return featureProperties;
	}

	public void setFeatureProperties(ArrayList<FeatureProperty> featureProperties) {
		this.featureProperties = featureProperties;
	}
	
	public boolean hasProperties() {
		return !featureProperties.isEmpty();
	}
	
	public boolean hasProperty(String needle) {
		for (FeatureProperty featureProperty : getfeatureProperties()) {
			if (featureProperty.getPropertyName() == needle) {
				return true;
			}
		}
		
		return false;
	}

	public ArrayList<ClaferConstraint> getFeatureConstraints() {
		return featureConstraints;
	}

	public void setFeatureConstraints(ArrayList<ClaferConstraint> featureConstraints) {
		this.featureConstraints = featureConstraints;
	}
	
	public boolean hasConstraints() {
		return !featureConstraints.isEmpty();
	}

	@Override
	public String toString() {
		StringBuilder strRepresentation = new StringBuilder();
		
		if (featureType == Constants.FeatureType.ABSTRACT) {
			strRepresentation.append("abstract ");
		}
		
		strRepresentation.append(getFeatureName());
		
		if (!getFeatureInheritance().isEmpty()) {
			strRepresentation.append(": ");
			strRepresentation.append(getFeatureInheritance());
		}
		
		for (FeatureProperty featureProperty : getfeatureProperties()) {
			strRepresentation.append("\n\t");
			strRepresentation.append(featureProperty.toString());			
		}
		
		for (ClaferConstraint featureConstraint : getFeatureConstraints()) {
			strRepresentation.append("\n\t");
			strRepresentation.append(featureConstraint.toString());
		}
		
		strRepresentation.append("\n");
		
		return strRepresentation.toString();
	}

	/**
	 * Implement features that this Clafer uses for inheritance or as property types if missing in the model and add them
	 * 
	 * @param claferModel
	 *        model to be updated with missing features
	 * @return a model containing only those features that have been added
	 */
	public ClaferModel implementMissingFeatures(ClaferModel claferModel) {
		ClaferModel addedFeatures = new ClaferModel();
		
		// find missing inherited feature
		if (!getFeatureInheritance().isEmpty()) {
			boolean parentFound = false;
			for (ClaferFeature cfrFeature : claferModel) {
				if (cfrFeature.getFeatureName().equals(getFeatureInheritance())) {
					parentFound = true;
					break;
				}
			}

			// remember missing inherited feature
			if (!parentFound) {
				ClaferFeature parentFeature = new ClaferFeature(Constants.FeatureType.ABSTRACT, getFeatureInheritance(), "");
				addedFeatures.add(parentFeature);
			}
		}

		// find missing property types
		for (FeatureProperty fp : getfeatureProperties()) {
			boolean propertyTypeFound = false;
			for (ClaferFeature cfrFeature : claferModel) {
				if (cfrFeature.getFeatureName().equals(fp.getPropertyType())) {
					propertyTypeFound = true;
					break;
				}
			}

			// remember missing property types			
			if (!fp.getPropertyType().isEmpty() && !propertyTypeFound) {
				ClaferFeature propertyTypeFeature = new ClaferFeature(Constants.FeatureType.CONCRETE, fp.getPropertyType(), "");
				addedFeatures.add(propertyTypeFeature);
			}
		}
		
		// add all missing features
		for (ClaferFeature cfrFeature : addedFeatures) {
			claferModel.add(cfrFeature);
		}
		
		return addedFeatures;
	}

	public ClaferModel getUnusedFeatures(ClaferModel claferModel) {
		// TODO switch logic, copy list and delete everything that is used
		ClaferModel unusedFeatures = new ClaferModel();
		
		for (ClaferFeature cfrFeature : claferModel) {
			// check usage of cfrFeature			
			boolean used = false;
			if (cfrFeature.hasProperties()) {
				used = true;
			}
			if (cfrFeature.hasConstraints()) {
				used = true;
			}
			
			// if abstract and somebody inherits -> used
			for (ClaferFeature refFeature : claferModel) {
				if (refFeature.getFeatureInheritance().equals(cfrFeature.getFeatureName())) {
					// usage found: refFeature inherits from cfrFeature
					used = true;
				}
			}

			for (ClaferFeature refFeature : claferModel) {
				for (FeatureProperty featureProp : refFeature.getfeatureProperties()) {
					if (featureProp.getPropertyType().equals(cfrFeature.getFeatureName())) {
						// usage found: featureProp is of type cfrFeature
						used = true;
					}
				}
			}

			if (!used) {
				unusedFeatures.add(cfrFeature);
			}
		}
		
		return unusedFeatures;
	}

	public void printModel(ClaferModel claferModel) {
		// TODO This should not be a class method
		StringBuilder sb = new StringBuilder();
		for (ClaferFeature cfrFeature : claferModel) {
			sb.append(cfrFeature.toString());
		}
		
		System.out.println(sb);
	}
	
}