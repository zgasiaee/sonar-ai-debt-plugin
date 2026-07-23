package io.github.aidebt.core;

import java.util.Locale;
import java.util.Set;

/** Framework-sensitive model and reproducibility knowledge kept separate from metric arithmetic. */
final class PythonMlRegistry {
  private static final Set<String> KNOWN_COMPONENTS = Set.of(
      "linearregression", "logisticregression", "ridge", "lasso", "elasticnet",
      "randomforestclassifier", "randomforestregressor", "gradientboostingclassifier",
      "gradientboostingregressor", "svc", "svr", "kneighborsclassifier", "kneighborsregressor",
      "kmeans", "dbscan", "decisiontreeclassifier", "decisiontreeregressor", "mlpclassifier",
      "mlpregressor", "xgbclassifier", "xgbregressor", "lgbmclassifier", "lgbmregressor",
      "catboostclassifier", "catboostregressor", "sequential", "model", "adaboostclassifier",
      "adaboostregressor", "extratreesclassifier", "extratreesregressor", "gaussiannb",
      "multinomialnb", "bernoullinb", "sgdclassifier", "sgdregressor", "isolationforest",
      "oneclasssvm", "pca", "truncatedsvd", "standardscaler", "minmaxscaler", "robustscaler");

  private static final Set<String> STOCHASTIC_COMPONENTS = Set.of(
      "randomforestclassifier", "randomforestregressor", "gradientboostingclassifier",
      "gradientboostingregressor", "decisiontreeclassifier", "decisiontreeregressor",
      "mlpclassifier", "mlpregressor", "xgbclassifier", "xgbregressor", "lgbmclassifier",
      "lgbmregressor", "catboostclassifier", "catboostregressor", "adaboostclassifier",
      "adaboostregressor", "extratreesclassifier", "extratreesregressor", "sgdclassifier",
      "sgdregressor", "isolationforest", "kmeans", "pca", "truncatedsvd");

  boolean isInitialization(PythonCall call) {
    String qualified = lower(call.qualifiedName());
    String simple = lower(call.simpleName());
    if (KNOWN_COMPONENTS.contains(simple)) return true;
    if (qualified.startsWith("transformers.") && simple.equals("from_pretrained")) return true;
    if ((qualified.startsWith("sklearn.") || qualified.startsWith("xgboost.")
        || qualified.startsWith("lightgbm.") || qualified.startsWith("catboost."))
        && (simple.endsWith("classifier") || simple.endsWith("regressor")
            || simple.endsWith("cluster") || simple.endsWith("transformer"))) return true;
    return (qualified.startsWith("tensorflow.keras.") || qualified.startsWith("keras."))
        && (simple.equals("model") || simple.equals("sequential"));
  }

  boolean requiresSeed(PythonCall call) {
    return STOCHASTIC_COMPONENTS.contains(lower(call.simpleName()));
  }

  boolean hasSeed(PythonCall call) {
    Set<String> keys = call.effectiveKeywordArguments();
    return keys.contains("random_state") || keys.contains("seed") || keys.contains("random_seed");
  }

  boolean requiresPinnedRevision(PythonCall call) {
    return lower(call.qualifiedName()).startsWith("transformers.") && "from_pretrained".equals(lower(call.simpleName()));
  }

  boolean hasPinnedRevision(PythonCall call) {
    return call.effectiveKeywordArguments().contains("revision");
  }

  String framework(PythonCall call) {
    String qualified = lower(call.qualifiedName());
    if (qualified.startsWith("sklearn.")) return "sklearn";
    if (qualified.startsWith("transformers.")) return "transformers";
    if (qualified.startsWith("tensorflow.") || qualified.startsWith("keras.")) return "keras";
    if (qualified.startsWith("xgboost.")) return "xgboost";
    if (qualified.startsWith("lightgbm.")) return "lightgbm";
    if (qualified.startsWith("catboost.")) return "catboost";
    return "inferred";
  }

  private static String lower(String value) { return value.toLowerCase(Locale.ROOT); }
}
