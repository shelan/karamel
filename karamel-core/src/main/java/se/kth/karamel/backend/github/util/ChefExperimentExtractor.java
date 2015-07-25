/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.karamel.backend.github.util;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import se.kth.karamel.backend.Experiment;
import se.kth.karamel.backend.Experiment.Code;
import se.kth.karamel.backend.github.Github;
import se.kth.karamel.common.Settings;
import se.kth.karamel.common.exception.KaramelException;
import se.kth.karamel.cookbook.metadata.KaramelFile;
import se.kth.karamel.cookbook.metadata.karamelfile.yaml.YamlKaramelFile;

/**
 * How to use. Invoke methods in this order: (1) @see ChefExperimentExtractor#parseAttributesAddToGit() (2) @see
 * ChefExperimentExtractor#parseRecipesAddToGit()
 *
 */
public class ChefExperimentExtractor {

  private static final String YAML_DEPENDENCY_PREFIX = "      - ";
  private static final String YAML_RECIPE_PREFIX = "  - recipe: ";

  // <AttrName, AttrValue> pair added to attributes/default.rb 
  private static final Map<String, String> attrs = new HashMap<>();
  private static final Map<String, Map<String, String>> configFiles = new HashMap<>();

  /**
   * Parses all scripts and config files and outputs to metadata.rb and attributes/default.rb the configuration values
   * found.
   *
   * @param owner org/user on github
   * @param repoName name of github repository
   * @param experiment input scripts/config filenames and content
   * @throws KaramelException
   */
  public static void parseAttributesAddToGit(String owner, String repoName, Experiment experiment)
      throws KaramelException {

    attrs.clear();
    configFiles.clear();

    StringBuilder recipeDescriptions = new StringBuilder();
    List<Code> experiments = experiment.getCode();

    Pattern pD = Pattern.compile("%%(.*)%%\\s*=\\s*(.*)\\s*");
    Matcher mD = pD.matcher(experiment.getDefaultAttributes());
    while (mD.find()) {
      String name = mD.group(1);
      String value = mD.group(2);
      attrs.put(name, value);
    }

    // Extract all the configFileNames: write them to metadata.rb later
    // Extract all the from the configFile contents: write them to attributes/default.rb later
    // No conflict detection for duplicate key-value pairs yet. Should be done in Javascript in Browser.
    for (Code code : experiments) {
      String configFileName = code.getConfigFileName();
      Map<String, String> cfs = configFiles.get(configFileName);
      if (cfs == null) {
        cfs = new HashMap<>();
        configFiles.put(configFileName, cfs);
      }
      recipeDescriptions.append("recipe            \"").append(repoName).append(Settings.COOOKBOOK_DELIMITER).
          append(code.getName()).append("\",  \"configFile=").append(configFileName)
          .append("; This experiment's name is ").append(code.getName()).append("\"").append(System.lineSeparator());
      String str = code.getConfigFileContents();
      Pattern p = Pattern.compile("%%(.*)%%\\s*=\\s*(.*)\\s*");
      Matcher m = p.matcher(str);
      while (m.find()) {
        String name = m.group(1);
        String value = m.group(2);
        cfs.put(name, value);
      }
    }

    String email = (Github.getEmail() == null) ? "karamel@karamel.io" : Github.getEmail();
    try {
      StringBuilder defaults_rb = CookbookGenerator.instantiateFromTemplate(
          Settings.CB_TEMPLATE_ATTRIBUTES_DEFAULT,
          "name", repoName,
          "user", experiment.getUser(),
          "group", experiment.getGroup(),
          "http_binaries", experiment.getUrlBinary()
      );

      // Add all key-value pairs from the config files to the default attributes
      for (String key : attrs.keySet()) {
        String entry = "default[:" + repoName + "][:" + key + "] = \"" + attrs.get(key) + "\"";
        defaults_rb.append(System.lineSeparator()).append(entry).append(System.lineSeparator());
      }

      StringBuilder metadata_rb = CookbookGenerator.instantiateFromTemplate(
          Settings.CB_TEMPLATE_METADATA,
          "name", repoName,
          "user", experiment.getUser(),
          "email", email,
          "depends", "",
          "resolve_ips", "",
          "build_command", experiment.getMavenCommand(),
          "url_binary", experiment.getUrlBinary(),
          "url_gitclone", experiment.getUrlGitClone(),
          "build_command", experiment.getMavenCommand(),
          "ip_params", "",
          "more_recipes", recipeDescriptions.toString()
      );

      for (String key : attrs.keySet()) {
        String entry = "attribute \"" + repoName + "/" + key + "\"," + System.lineSeparator()
            + ":description => \"" + key + " parameter value\"," + System.lineSeparator()
            + ":type => \"string\"";
        metadata_rb.append(System.lineSeparator()).append(entry).append(System.lineSeparator());
      }

      // 3. write them to files and push to github
      Github.addFile(owner, repoName, "attributes/default.rb", defaults_rb.toString());
      Github.addFile(owner, repoName, "metadata.rb", metadata_rb.toString());

    } catch (IOException ex) {
      Logger.getLogger(ChefExperimentExtractor.class.getName()).log(Level.SEVERE, null, ex);
      throw new KaramelException(ex.getMessage());
    }
  }

  /**
   * Parses the user-defined script files and for each script, a recipe file is generated and added to the git repo.
   *
   * @param owner
   * @param repoName
   * @param experimentContext
   * @throws se.kth.karamel.common.exception.KaramelException
   * @throws KaramelExceptionntents.toString()); // Update Karamel
   */
  public static void parseRecipesAddToGit(String owner, String repoName, Experiment experimentContext)
      throws KaramelException {

    try {

      StringBuilder kitchenContents = CookbookGenerator.instantiateFromTemplate(
          Settings.CB_TEMPLATE_KITCHEN_YML,
          "name", repoName
      );
      Github.addFile(owner, repoName, ".kitchen.yml", kitchenContents.toString());


      List<Code> experiments = experimentContext.getCode();

      StringBuilder recipeNames = new StringBuilder();
      for (Code experiment : experiments) {
        String recipeName = experiment.getName();
        if (recipeName.compareToIgnoreCase("experiment") != 0) {
          recipeNames.append(repoName).append(Settings.COOOKBOOK_DELIMITER).append(recipeName).append(",");
        }
      }
      String namesOfRecipes = recipeNames.toString();
      if (namesOfRecipes.length() > 0 && namesOfRecipes.charAt(namesOfRecipes.length() - 1) == ',') {
        namesOfRecipes = namesOfRecipes.substring(0, namesOfRecipes.length() - 1);
        namesOfRecipes = namesOfRecipes.replaceAll(",", System.lineSeparator() + YAML_RECIPE_PREFIX);
        namesOfRecipes = YAML_RECIPE_PREFIX + namesOfRecipes;
      }

      String localDependencies = experimentContext.getLocalDependencies();
      if (!localDependencies.isEmpty()) {
        localDependencies = localDependencies.replaceAll(" ", "");
        localDependencies = localDependencies.replaceAll(",", System.lineSeparator() + YAML_DEPENDENCY_PREFIX);
        localDependencies = YAML_DEPENDENCY_PREFIX + localDependencies;
      }
      String globalDependencies = experimentContext.getGlobalDependencies();
      if (!globalDependencies.isEmpty()) {
        globalDependencies = globalDependencies.replaceAll(" ", "");
        globalDependencies = globalDependencies.replaceAll(",", System.lineSeparator() + YAML_DEPENDENCY_PREFIX);
        globalDependencies = YAML_DEPENDENCY_PREFIX + globalDependencies;
      }
      StringBuilder karamelContents = CookbookGenerator.instantiateFromTemplate(
          Settings.CB_TEMPLATE_KARAMELFILE,
          "name", repoName,
          "local_dependencies", localDependencies,
          "global_dependencies", globalDependencies,
          "next_recipes", namesOfRecipes
      );
      KaramelFile karamelFile = new KaramelFile(karamelContents.toString());
      // Update Karamelfile with dependencies from the cluster definition
      String ymlString = experimentContext.getClusterDefinition();
//      List<String> clusterDependencies = new ArrayList<>();
//      if (!ymlString.isEmpty()) {
//        JsonCluster jsonCluster = ClusterDefinitionService.yamlToJsonObject(ymlString);
//        for (JsonGroup g : jsonCluster.getGroups()) {
//          for (JsonCookbook cb : g.getCookbooks()) {
//            for (JsonRecipe r : cb.getRecipes()) {
//              clusterDependencies.add(r.getCanonicalName());
//            }
//          }
//        }
//      }

      String berksfile = experimentContext.getBerksfile();
      StringBuilder berksDependencies = new StringBuilder();
      if (!berksfile.isEmpty()) {
        int curPos = 0;
        int pos = 0;
        while (pos != -1) {
          pos = berksfile.indexOf("\"", curPos);
          curPos = pos + 1;
          pos = berksfile.indexOf("\"", curPos);
          if (pos != -1) {
            berksDependencies.append(berksfile.substring(curPos, pos - 1)).append(System.lineSeparator());
          }
          curPos = pos + 1;
        }
      }

      String[] tokens = berksfile.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1);
      for (String s : tokens) {
        String quotesStripped = s.replaceAll("\"", "");
        berksDependencies.append(quotesStripped).append(System.lineSeparator());
      }

      StringBuilder berksContents = CookbookGenerator.instantiateFromTemplate(
          Settings.CB_TEMPLATE_BERKSFILE,
          "berks_dependencies", berksDependencies.toString()
      );

      Github.addFile(owner, repoName, "Berksfile", berksContents.toString());

      Map<String, String> expConfigFileNames = new HashMap<>();
      Map<String, String> expConfigFilePaths = new HashMap<>();

      // 2. write them to recipes/default.rb and metadata.rb
      for (Code experiment : experiments) {
        String experimentName = experiment.getName();
        String configFilePath = experiment.getConfigFileName();
        String configFileContents = experiment.getConfigFileContents();

        String configFileName = configFilePath;
        int filePos = configFileName.lastIndexOf("/");
        if (filePos != -1) {
          configFileName = configFileName.substring(filePos + 1);
        }

//        YamlDependency yd = new YamlDependency();
//        if (!ymlString.isEmpty()) {
//          yd.setGlobal(clusterDependencies);
//          yd.setRecipe(repoName + Settings.COOOKBOOK_DELIMITER + experimentName);
//        }
        String email = (Github.getEmail() == null) ? "karamel@karamel.io" : Github.getEmail();

        StringBuilder recipe_rb = CookbookGenerator.instantiateFromTemplate(
            Settings.CB_TEMPLATE_RECIPE_EXPERIMENT,
            "name", experimentName,
            //            "pre_chef_commands", experiment.getPreScriptChefCode(),
            "interpreter", experiment.getScriptType(),
            "user", experimentContext.getUser(),
            "group", experimentContext.getGroup(),
            "script_contents", experiment.getScriptContents()
        );

        String recipeContents = recipe_rb.toString();

        // Replace all parameters with chef attribute values
        for (String attr : attrs.keySet()) {
          recipeContents = recipeContents.replaceAll("%%" + attr + "%%", "#{node[:" + repoName + "][:" + attr + "]}");
        }

        for (String attr : attrs.keySet()) {
          configFileContents = configFileContents.replaceAll("%%" + attr + "%%",
              "<%= node[:" + repoName + "][:" + attr + "] =>");
        }

        if (!configFilePath.isEmpty()) {
          expConfigFileNames.put(experimentName, configFileName);
          expConfigFilePaths.put(experimentName, configFilePath);
        }

        // 3. write them to files and push to github
        Github.addFile(owner, repoName, "recipes" + File.separator + experimentName + ".rb", recipeContents);
        Github.addFile(owner, repoName,
            "templates" + File.separator + "defaults" + File.separator + configFileName + ".erb", configFileContents);

//        if (!ymlString.isEmpty()) {
//          karamelFile.getDependencies().add(yd);
//        }
      }

      StringBuilder configFilesTemplateDefns = new StringBuilder();
      for (String expName : expConfigFileNames.keySet()) {
        String configFilePath = expConfigFileNames.get(expName);
        String configFileName = expConfigFileNames.get(expName);
        StringBuilder configProps = CookbookGenerator.instantiateFromTemplate(
            Settings.CB_TEMPLATE_CONFIG_PROPS,
            "name", expName,
            "configFileName", configFileName,
            "configFilePath", configFilePath,
            "ip_params", ""
        );
        configFilesTemplateDefns.append(configProps).append(System.lineSeparator());
      }

      StringBuilder install_rb = CookbookGenerator.instantiateFromTemplate(
          Settings.CB_TEMPLATE_RECIPE_INSTALL,
          "name", repoName,
          "checksum", "",
          "resolve_ips", "",
          "setup_code", experimentContext.getExperimentSetupCode(),
          "config_files", configFilesTemplateDefns.toString()
      );

      Github.addFile(owner, repoName, "recipes/install.rb", install_rb.toString());

      DumperOptions options = new DumperOptions();
      options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
      Representer r = new Representer();
      r.addClassTag(KaramelFile.class, Tag.MAP);
      Yaml karamelYml = new Yaml(new Constructor(YamlKaramelFile.class), r, options);
      String karamelFileContents = karamelYml.dump(karamelFile);
      Github.addFile(owner, repoName, "Karamelfile", karamelFileContents);

    } catch (IOException ex) {
      Logger.getLogger(ChefExperimentExtractor.class.getName()).log(Level.SEVERE, null, ex);
      throw new KaramelException(ex.getMessage());
    }

  }
}
