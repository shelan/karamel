/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.karamel.common.cookbookmeta;

import se.kth.karamel.common.util.Settings;

public class ExperimentRecipe {

  private final String recipeName;
  private final String scriptContents;
  private final String scriptType;
  private final String configFileName;
  private final String configFileContents;

  public ExperimentRecipe(String recipeName, String scriptType, 
      String scriptContents, String configFileName, String configFileContents) {
    if (recipeName.contains(Settings.COOKBOOK_DELIMITER)) {
      recipeName = recipeName.split(Settings.COOKBOOK_DELIMITER)[1];
    }
    this.recipeName = recipeName;
    this.scriptContents = scriptContents;
    this.scriptType = scriptType;
    this.configFileName = configFileName;
    this.configFileContents = configFileContents;
  }

  public String getScriptContents() {
    return scriptContents;
  }

  public String getRecipeName() {
    return recipeName;
  }

  public String getScriptType() {
    return scriptType;
  }

  public String getConfigFileContents() {
    return configFileContents;
  }

  public String getConfigFileName() {
    return configFileName;
  }

}
