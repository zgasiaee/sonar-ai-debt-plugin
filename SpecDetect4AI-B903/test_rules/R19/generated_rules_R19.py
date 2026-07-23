import ast
import sys

torch_imported = False
torch_used = False
deterministic_used = False
def reset_torch_flags():
    global torch_imported, torch_used, deterministic_used
    torch_imported = False
    torch_used = False
    deterministic_used = False

def track_torch_import(node):
    global torch_imported
    if isinstance(node, ast.Import):
        for alias in node.names:
            if alias.name == 'torch':
                torch_imported = True
    elif isinstance(node, ast.ImportFrom):
        if node.module and node.module.startswith('torch'):
            torch_imported = True

def useDeterministic(node):
    global deterministic_used
    if not isinstance(node, ast.Call):
        return False
    if isinstance(node.func, ast.Attribute) and node.func.attr == 'use_deterministic_algorithms':
        if node.args and isinstance(node.args[0], ast.Constant) and node.args[0].value is True:
            deterministic_used = True
            return True
    return False

def isRelevantTorchCall(node):
    global torch_used
    if not isinstance(node, ast.Call):
        return False
    full_path = get_full_attr_path(node.func)
    if full_path and full_path.startswith("torch."):
        torch_used = True
        return True
    return False

def get_full_attr_path(expr):
    parts = []
    while isinstance(expr, ast.Attribute):
        parts.insert(0, expr.attr)
        expr = expr.value
    if isinstance(expr, ast.Name):
        parts.insert(0, expr.id)
    return '.'.join(parts)

def customCheckTorchDeterminism(ast_node):
    reset_torch_flags()
    for node in ast.walk(ast_node):
        track_torch_import(node)
        isRelevantTorchCall(node)
        useDeterministic(node)
    #log(f"torch_imported={torch_imported}, torch_used={torch_used}, deterministic_used={deterministic_used}")
    return torch_imported and torch_used and not deterministic_used

def report(message):
    print('REPORT:', message, flush=True)

def log(message):
    print('LOG:', message, flush=True)

def report_with_line(message, node):
    line = getattr(node, 'lineno', '?')
    report(message.format(lineno=line))

def add_parent_info(node, parent=None):
    node.parent = parent
    for child in ast.iter_child_nodes(node):
        add_parent_info(child, node)
    if parent is None:
       init_train_lines(node)

def gather_scale_sensitive_ops(ast_node):
    scale_sensitive_ops = {
        'PCA', 'SVC', 'SGDClassifier', 'SGDRegressor', 'MLPClassifier',
        'ElasticNet', 'Lasso', 'Ridge', 'KMeans', 'KNeighborsClassifier',
        'LogisticRegression'
    }
    ops = {}
    for stmt in ast.walk(ast_node):
        if isinstance(stmt, ast.Assign):
            if len(stmt.targets) == 1 and isinstance(stmt.targets[0], ast.Name):
                var_name = stmt.targets[0].id
                value = stmt.value
                if isinstance(value, ast.Call):
                    func = value.func
                    if isinstance(func, ast.Name) and func.id in scale_sensitive_ops:
                        ops[var_name] = func.id
                    elif isinstance(func, ast.Attribute) and func.attr in scale_sensitive_ops:
                        ops[var_name] = func.attr
    return ops

def isScaleSensitiveFit(call, variable_ops):
    if not isinstance(call, ast.Call):
        return False
    if not (isinstance(call.func, ast.Attribute) and call.func.attr == 'fit'):
        return False
    callee = call.func.value
    if isinstance(callee, ast.Name):
        return callee.id in variable_ops
    return False

def isChainedIndexingBase(node):
    """
    Détecte les patterns chain indexing :
      - df[...][...]
      - df[...][...].method()
    Exclut .values[...] et .to_numpy()[...], .str[...] et .apply[...]
    """
    if not isinstance(node, ast.Subscript):
        return False

    value = node.value

    # Exclure accès .values, .to_numpy, .str, .apply, .dt, .map, etc.
    skip_attrs = {'values', 'to_numpy', 'str', 'dt', 'apply', 'map', 'squeeze'}

    # Traverse la chaîne d'attributs/méthodes entre le Subscript actuel et le précédent Subscript
    while isinstance(value, (ast.Attribute, ast.Call)):
        # Si on rencontre .str, .apply, etc. => on NE FLAG PAS
        if isinstance(value, ast.Attribute) and value.attr in skip_attrs:
            return False
        # Si c'est un appel de méthode, on check la fonction puis continue sur .value
        if hasattr(value, "value"):
            value = value.value
        elif hasattr(value, "func"):
            value = value.func
        else:
            break

    # Si on atteint un Subscript (df[...][...])
    if isinstance(value, ast.Subscript):
        return True

    return False
def get_scope_dataframe_vars(node):
    current = node
    while current is not None and not isinstance(current, (ast.FunctionDef, ast.Module)):
        current = getattr(current, 'parent', None)

    local_vars = set()
    series_vars = set()

    dataframe_creators = {
        'DataFrame', 'from_dict', 'from_records',
        'read_csv', 'read_json', 'read_excel',
        'read_sql', 'read_parquet', 'read_feather',
        'read_table', 'concat', 'merge'
    }
    series_creators = {'Series'}

    # 1. Détection explicite des DataFrames et Series Pandas
    for stmt in ast.walk(current):
        if isinstance(stmt, ast.Assign) and len(stmt.targets) == 1:
            target = stmt.targets[0]
            if not isinstance(target, ast.Name):
                continue
            var_name = target.id
            val = stmt.value

            if isinstance(val, ast.Call):
                func = val.func
                if isinstance(func, ast.Attribute):
                    # Ex: pd.read_csv(...) ou pd.Series(...)
                    if isinstance(func.value, ast.Name) and func.value.id == 'pd':
                        if func.attr in dataframe_creators:
                            local_vars.add(var_name)
                        elif func.attr in series_creators:
                            series_vars.add(var_name)
                    # Ex: pd.DataFrame.from_dict(...) ou pd.Series.from_array(...)
                    elif isinstance(func.value, ast.Attribute):
                        if func.value.attr == 'DataFrame' and getattr(func.value.value, 'id', '') == 'pd':
                            if func.attr in dataframe_creators:
                                local_vars.add(var_name)
                        if func.value.attr == 'Series' and getattr(func.value.value, 'id', '') == 'pd':
                            if func.attr in series_creators:
                                series_vars.add(var_name)
                elif isinstance(func, ast.Name):
                    if func.id == 'DataFrame':
                        local_vars.add(var_name)
                    elif func.id == 'Series':
                        series_vars.add(var_name)

    # 2. Propagation du statut DataFrame/Series via alias, accès colonne ou méthode pandas
    for stmt in ast.walk(current):
        if isinstance(stmt, ast.Assign) and len(stmt.targets) == 1:
            target = stmt.targets[0]
            if not isinstance(target, ast.Name):
                continue
            var_name = target.id
            val = stmt.value

            # df2 = df.method(...) ou df2 = df[...]
            if isinstance(val, (ast.Call, ast.Subscript)):
                base = get_base_name(val)
                # Cas DataFrame
                if base in local_vars:
                    local_vars.add(var_name)
                # Cas Series : accès à une colonne d'un DataFrame connu
                if base in local_vars and isinstance(val, ast.Subscript):
                    series_vars.add(var_name)
                # Cas alias de Series
                if base in series_vars:
                    series_vars.add(var_name)

    # 3. On EXCLUT explicitement les dict, defaultdict, list, set...
    for stmt in ast.walk(current):
        if isinstance(stmt, ast.Assign) and len(stmt.targets) == 1:
            target = stmt.targets[0]
            if not isinstance(target, ast.Name):
                continue
            var_name = target.id
            val = stmt.value
            if isinstance(val, ast.Call) and isinstance(val.func, ast.Name):
                if val.func.id in {'dict', 'defaultdict', 'list', 'set'}:
                    if var_name in local_vars:
                        local_vars.remove(var_name)
                    if var_name in series_vars:
                        series_vars.remove(var_name)

    # 4. Propagation simple des alias (alias = df ou alias = series)
    for stmt in ast.walk(current):
        if isinstance(stmt, ast.Assign) and len(stmt.targets) == 1:
            target = stmt.targets[0]
            if not isinstance(target, ast.Name):
                continue
            var_name = target.id
            val = stmt.value
            if isinstance(val, ast.Name):
                if val.id in local_vars:
                    local_vars.add(var_name)
                if val.id in series_vars:
                    series_vars.add(var_name)

    # Retourne l'union
    return local_vars | series_vars
def get_base_name(expr):
    while isinstance(expr, (ast.Subscript, ast.Attribute, ast.Call)):
        if hasattr(expr, "value"):
            expr = expr.value
        elif hasattr(expr, "func"):
            expr = expr.func
        else:
            break
    if isinstance(expr, ast.Name):
        return expr.id
    return None
def isDataFrameVariable(var, node):
    if isinstance(var, str):
        base = var
    else:
        base = get_base_name(var)
    if base is None:
        return False
    scope_vars = get_scope_dataframe_vars(node)
    return base in scope_vars

def gather_scaled_vars(ast_node):
    scaled_vars = set()
    known_scalers = {
        'StandardScaler','MinMaxScaler','RobustScaler','Normalizer',
        'MaxAbsScaler','PowerTransformer','QuantileTransformer'
    }

    scaler_map = {}
    for stmt in ast.walk(ast_node):
        if isinstance(stmt, ast.Assign):
            if len(stmt.targets) == 1 and isinstance(stmt.targets[0], ast.Name):
                var_name = stmt.targets[0].id
                value = stmt.value
                if isinstance(value, ast.Call):
                    func = value.func
                    # ex: scaler = StandardScaler()
                    if isinstance(func, ast.Name) and func.id in known_scalers:
                        scaler_map[var_name] = func.id
                    elif isinstance(func, ast.Attribute) and func.attr in known_scalers:
                        scaler_map[var_name] = func.attr

    for stmt in ast.walk(ast_node):
        if isinstance(stmt, ast.Assign) and len(stmt.targets) == 1:
            target = stmt.targets[0]
            if isinstance(target, ast.Name):
                if isinstance(stmt.value, ast.Call):
                    if isinstance(stmt.value.func, ast.Attribute):
                        # ex: scaler.fit_transform(X) ou StandardScaler().fit_transform(X)
                        if stmt.value.func.attr == 'fit_transform':
                            base = stmt.value.func.value  # ex. ast.Name(id='scaler') ou ast.Call(...)
                            if isinstance(base, ast.Name):
                                # case: scaler.fit_transform(X)
                                if base.id in scaler_map:
                                    scaled_vars.add(target.id)
                                else:
                                    base_func = get_base_name(base)
                                    if base_func in known_scalers:
                                        scaled_vars.add(target.id)
                            else:
                                # case: StandardScaler().fit_transform(X)
                                base_func = get_base_name(base)
                                if base_func in known_scalers:
                                    scaled_vars.add(target.id)
    return scaled_vars

def call_uses_scaled_data(call_node, scaled_vars):
    if not isinstance(call_node, ast.Call):
        return False
    for arg in call_node.args:
        if isinstance(arg, ast.Name) and arg.id in scaled_vars:
            return True
    for kw in call_node.keywords:
        if isinstance(kw.value, ast.Name) and kw.value.id in scaled_vars:
            return True
    return False

def hasPrecedingScaler(call, scaled_vars=None):
    if scaled_vars:
        if call_uses_scaled_data(call, scaled_vars):
            return True
    scalers = {
        'StandardScaler', 'MinMaxScaler', 'RobustScaler', 'Normalizer',
        'MaxAbsScaler', 'PowerTransformer', 'QuantileTransformer'
    }
    current = call
    while current:
        current = getattr(current, 'parent', None)
        if isinstance(current, ast.Assign):
            value = current.value
            if isinstance(value, ast.Call):
                if isinstance(value.func, ast.Name) and value.func.id in scalers:
                    return True
                elif isinstance(value.func, ast.Attribute) and value.func.attr in scalers:
                    return True
    return False

def parse_pipeline_steps(node):
    funcs = []
    if isinstance(node, ast.Call):
        base_name = None
        if isinstance(node.func, ast.Name):
            base_name = node.func.id
        elif isinstance(node.func, ast.Attribute):
            base_name = node.func.attr
        if base_name:
            funcs.append(base_name)
        for arg in node.args:
            funcs.extend(parse_pipeline_steps(arg))
        for kw in node.keywords:
            funcs.extend(parse_pipeline_steps(kw.value))
    elif isinstance(node, (ast.List, ast.Tuple)):
        for elt in node.elts:
            funcs.extend(parse_pipeline_steps(elt))
    elif isinstance(node, ast.Dict):
        for key, value in zip(node.keys, node.values):
            funcs.extend(parse_pipeline_steps(value))
    elif isinstance(node, ast.keyword):
        funcs.extend(parse_pipeline_steps(node.value))
    return funcs

def isPartOfValidatedPipeline(call):
    scalers = {
        'StandardScaler', 'MinMaxScaler', 'RobustScaler', 'Normalizer',
        'MaxAbsScaler', 'PowerTransformer', 'QuantileTransformer'
    }
    sensitive_ops = {
        'PCA', 'SVC', 'SGDClassifier', 'SGDRegressor', 'MLPClassifier',
        'ElasticNet', 'Lasso', 'Ridge', 'KMeans', 'KNeighborsClassifier',
        'LogisticRegression'
    }
    parent = call
    while parent:
        if isinstance(parent, ast.Call) and isinstance(parent.func, ast.Name):
            if parent.func.id in {'Pipeline', 'make_pipeline'}:
                all_funcs = []
                for arg in parent.args:
                    all_funcs.extend(parse_pipeline_steps(arg))
                for kw in parent.keywords:
                    all_funcs.extend(parse_pipeline_steps(kw.value))
                has_scaler = any(func in scalers for func in all_funcs)
                has_sensitive_op = any(func in sensitive_ops for func in all_funcs)
                return (has_scaler and has_sensitive_op)
        parent = getattr(parent, 'parent', None)
    return False

def isDataFrameColumnAssignment(node):
    if not isinstance(node, ast.Assign):
        return False
    if len(node.targets) != 1:
        return False
    target = node.targets[0]
    if not isinstance(target, ast.Subscript):
        return False
    base_name = get_base_name(target.value)
    if not isDataFrameVariable(base_name, target.value):
        return False
    return True

def isAssignedLiteral(node, val):
    if not isinstance(node, ast.Assign):
        return False
    assigned_value = node.value
    if not isinstance(assigned_value, ast.Constant):
        return False
    return assigned_value.value == val

# Fonctions ajoutées (absentes du premier header initial)
def isDataFrameMerge(node):
    return (isinstance(node, ast.Call) and
            hasattr(node, 'func') and
            getattr(node.func, 'attr', '') == 'merge' and
            isinstance(node.func.value, ast.Name) and
            node.func.value.id in get_scope_dataframe_vars(node))

def singleParam(node):
    return (len(node.args) + len(node.keywords)) == 1

def isApiMethod(node):
    """
    Détecte les appels d’API qui doivent :
      • soit être exécutés avec « inplace=True » (DataFrame Pandas),
      • soit ré‑affecter leur résultat (NumPy ou Pandas).

    Renvoie True si l’appel entre dans l’un de ces deux cas.
    """
    if not (isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)):
        return False

    attr = node.func.attr
    base = get_base_name(node.func.value)

    # ----------  NumPy -------------------------------------------------
    numpy_methods_requiring_assignment = {'clip', 'sort', 'argsort'}
    if base == 'np' and attr in numpy_methods_requiring_assignment:
        return True

    # ----------  DataFrame Pandas -------------------------------------
    api_methods = {
        'drop', 'dropna', 'sort_values', 'replace',
        'clip', 'sort', 'argsort',
        'detach', 'cpu', 'clone', 'numpy',
        'transform', 'fit_transform',
        'traverse', 'strip', 'rstrip', 'lstrip', 'lower', 'upper'
    }
    if attr in api_methods and base in get_scope_dataframe_vars(node):
        return True

    return False


def hasInplaceTrue(node):
    if isinstance(node, ast.Call):
        for kw in node.keywords:
            if kw.arg == 'inplace' and isinstance(kw.value, ast.Constant) and kw.value.value is True:
                return True
    return False

def isResultUsed(node):
    parent = getattr(node, 'parent', None)
    if isinstance(parent, ast.Expr):
        return False
    if isinstance(parent, (ast.Assign, ast.AnnAssign, ast.AugAssign)):
        return True
    if isinstance(parent, ast.Return):
        return True
    if isinstance(parent, ast.Call):
        return True
    if isinstance(parent, (ast.Attribute, ast.Subscript)):
        return isResultUsed(parent)
    if isinstance(parent, (ast.List, ast.Tuple, ast.Dict, ast.Set)):
        return True
    return False

def isBinOp(node):
    return isinstance(node, ast.BinOp)

def isTfTile(node):
    return (
    hasattr(node, 'func') and
    getattr(node.func, 'attr', '') == 'tile' and
    isinstance(node.func.value, ast.Name) and
    node.func.value.id == 'tf')

def isSubscript(node):
    return isinstance(node, ast.Subscript)

def extract_metric_name(node):
    if isinstance(node, ast.Call):
        if hasattr(node.func, 'attr') and node.func.attr == 'make_scorer':
            for arg in node.args:
                candidate = extract_metric_name(arg)
                if candidate is not None:
                    return candidate
            return None
        else:
            return extract_metric_name(node.func)
    if isinstance(node, ast.Attribute):
        return node.attr
    if isinstance(node, ast.Name):
        return node.id
    return None

def isMetricCall(node):
    return isinstance(node, ast.Call) and hasattr(node, 'func') and hasattr(node.func, 'attr')

def isThresholdDependent(node):
    metric_name = extract_metric_name(node)
    return metric_name in {
        'f1_score', 'precision_score', 'recall_score', 'accuracy_score',
        'specificity', 'balanced_accuracy', 'jaccard_score',
        'confusion_matrix', 'brier_score_loss'
    }

def isThresholdIndependent(node):
    metric_name = extract_metric_name(node)
    return metric_name in {
        'mean_absolute_error', 'mean_squared_error', 'root_mean_squared_error',
        'r2_score', 'max_error', 'mean_absolute_percentage_error',
        'roc_auc_score', 'roc_curve', 'pr_auc_score',
        'precision_recall_curve', 'log_loss', 'hinge_loss', 'auc'
    }

def isCompare(node):
    return isinstance(node, ast.Compare)

def hasNpNanComparator(node):
    if not isinstance(node, ast.Compare):
        return False
    for comparator in node.comparators:
        if isinstance(comparator, ast.Attribute):
            if (isinstance(comparator.value, ast.Name) and
                comparator.value.id == 'np' and
                comparator.attr == 'nan'):
                return True
    return False

def isNumpyVariable(node):
    return isinstance(node, ast.Name) and node.id == 'np'

def isValuesAccess(node):
    """
    Retourne True si le nœud est un accès d'attribut sur '.values'
    """
    return isinstance(node, ast.Attribute) and node.attr == 'values'

def isPandasReadCall(node):
    pandas_read_methods = {'read_csv', 'read_json', 'read_sql', 'read_table', 'read_excel', 'read_parquet'}
    if isinstance(node, ast.Call):
        # Cas pd.read_csv(...) ou pandas.read_csv(...)
        if isinstance(node.func, ast.Attribute):
            if (isinstance(node.func.value, ast.Name)
                and node.func.value.id in {'pd', 'pandas'}
                and node.func.attr in pandas_read_methods):
                return True
        # Cas import direct : read_csv(...)
        if isinstance(node.func, ast.Name):
            if node.func.id in pandas_read_methods:
                return True
    return False
def hasKeyword(node, keyword_name):
   if isinstance(node, ast.Call):
       return any(kw.arg == keyword_name for kw in node.keywords)
   return False
def isDotCall(node):
   if not isinstance(node, ast.Call):
       return False
   if not isinstance(node.func, ast.Attribute):
       return False
   if node.func.attr != "dot":
       return False
   if not (isinstance(node.func.value, ast.Name) and node.func.value.id == "np"):
       return False
   return True
def isMatrix2D(node):
   if not isinstance(node, ast.Call):
       return False
   if len(node.args) != 2:
       return False
   return True
def isForLoop(node):
   return isinstance(node, ast.For)

def isFunctionDef(node):
   return isinstance(node, ast.FunctionDef)
def usesIterrows(node):
   return (isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == 'iterrows')
def usesItertuples(node):
   return (isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == 'itertuples')
def usesPythonLoopOnTensorFlow(loop_node):
    if not isinstance(loop_node, ast.For):
        return False
    
    # 1. Collecter toutes les variables assignées à un tenseur TensorFlow
    tf_vars = set()
    root = loop_node
    while hasattr(root, 'parent') and root.parent:
        root = root.parent

    for stmt in ast.walk(root):
        if isinstance(stmt, ast.Assign):
            if isinstance(stmt.value, ast.Call):
                func = stmt.value.func
                if isinstance(func, ast.Attribute):
                    base_name = get_base_name(func.value)
                    if base_name == 'tf':
                        for target in stmt.targets:
                            if isinstance(target, ast.Name):
                                tf_vars.add(target.id)

    # 2. Vérifier si la boucle fait des opérations sur ces variables TensorFlow
    for stmt in ast.walk(loop_node):
        if isinstance(stmt, ast.AugAssign) and isinstance(stmt.op, ast.Add):
            if isinstance(stmt.value, ast.Subscript):
                var_name = get_base_name(stmt.value)
                if var_name in tf_vars:
                    return True
    return False
def isTensorFlowTensor(node):
   if isinstance(node, ast.Name):
       var_name = node.id.lower()
       return 'tf' in var_name or 'tensor' in var_name
   return False
def isRandomCall(call):
    if not isinstance(call, ast.Call):
        return False
    if isinstance(call.func, ast.Name) and call.func.id == 'DataLoader':
        for kw in call.keywords:
           if kw.arg == 'shuffle' and isinstance(kw.value, ast.Constant) and kw.value.value is True:
               return True
    if not isinstance(call.func, ast.Attribute):
        return False

    rand_funcs = [
        ('np', 'random', {'random', 'rand', 'randn', 'randint', 'normal',
                          'uniform', 'sample', 'choice', 'shuffle', 'permutation'}),
        ('torch', None, {'rand', 'randn', 'randint', 'random'}),
        ('tf', 'random', {'normal', 'uniform', 'shuffle'}),
        ('random', None, {'randint', 'choice', 'shuffle', 'random', 'uniform'}),
        ('sklearn', 'utils', {'shuffle'}),
        ('sklearn', 'model_selection', {'train_test_split'}),
        ('sklearn', 'metrics', {'make_scorer'}),
        ('df', None, {'randomSplit'}),
    ]

    for lib, submodule, funcs in rand_funcs:
        if submodule:
            if (isinstance(call.func.value, ast.Attribute) and
                isinstance(call.func.value.value, ast.Name) and
                call.func.value.value.id == lib and
                call.func.value.attr == submodule and
                call.func.attr in funcs):
                return True
        else:
            if (isinstance(call.func.value, ast.Name) and
                call.func.value.id == lib and
                call.func.attr in funcs):
                return True

    return False

def seedSet(call):
    if not isinstance(call, ast.Call):
        return False
    if not isinstance(call.func, ast.Attribute):
        return False

    # np.random.seed(...)
    if (isinstance(call.func.value, ast.Attribute) and
        isinstance(call.func.value.value, ast.Name) and
        call.func.value.value.id == 'np' and
        call.func.value.attr == 'random' and
        call.func.attr == 'seed'):
        return True

    # tf.random.set_seed(...)
    if (isinstance(call.func.value, ast.Attribute) and
        isinstance(call.func.value.value, ast.Name) and
        call.func.value.value.id == 'tf' and
        call.func.value.attr == 'random' and
        call.func.attr == 'set_seed'):
        return True

    # torch.manual_seed(...)
    if (isinstance(call.func.value, ast.Name) and
        call.func.value.id == 'torch' and
        call.func.attr == 'manual_seed'):
        return True

    # random.seed(...)
    if (isinstance(call.func.value, ast.Name) and
        call.func.value.id == 'random' and
        call.func.attr == 'seed'):
        return True

    return False
    # np.random.seed(...)
    if (isinstance(call.func.value, ast.Attribute) and
        isinstance(call.func.value.value, ast.Name) and
        call.func.value.value.id == 'np' and
        call.func.value.attr == 'random' and
        call.func.attr == 'seed'):
        return True

    # tf.random.set_seed(...)
    if (isinstance(call.func.value, ast.Attribute) and
        isinstance(call.func.value.value, ast.Name) and
        call.func.value.value.id == 'tf' and
        call.func.value.attr == 'random' and
        call.func.attr == 'set_seed'):
        return True

    # torch.manual_seed(...)
    if (isinstance(call.func.value, ast.Name) and
        call.func.value.id == 'torch' and
        call.func.attr == 'manual_seed'):
        return True

    # random.seed(...)
    if (isinstance(call.func.value, ast.Name) and
        call.func.value.id == 'random' and
        call.func.attr == 'seed'):
        return True

    return False
def hasRandomState(call):
    if not isinstance(call, ast.Call):
        return False
    for kw in call.keywords:
        if kw.arg == 'random_state':
            if isinstance(kw.value, ast.Constant):
                return kw.value.value is not None
            return True  
    return False
def global_seed_set(ast_node, lib):
    seeds = set()
    for stmt in ast.walk(ast_node):
        if isinstance(stmt, ast.Call):
            if isinstance(stmt.func, ast.Attribute):
                # np.random.seed(...)
                if (isinstance(stmt.func.value, ast.Attribute) and
                    isinstance(stmt.func.value.value, ast.Name) and
                    stmt.func.value.value.id == 'np' and
                    stmt.func.value.attr == 'random' and
                    stmt.func.attr == 'seed'):
                    seeds.add('numpy')
                # torch.manual_seed(...)
                elif (isinstance(stmt.func.value, ast.Name) and
                      stmt.func.value.id == 'torch' and
                      stmt.func.attr == 'manual_seed'):
                    seeds.add('torch')
                # tf.random.set_seed(...)
                elif (isinstance(stmt.func.value, ast.Attribute) and
                      isinstance(stmt.func.value.value, ast.Name) and
                      stmt.func.value.value.id == 'tf' and
                      stmt.func.value.attr == 'random' and
                      stmt.func.attr == 'set_seed'):
                    seeds.add('tensorflow')
                # random.seed(...)
                elif (isinstance(stmt.func.value, ast.Name) and
                      stmt.func.value.id == 'random' and
                      stmt.func.attr == 'seed'):
                    seeds.add('random')
    return lib in seeds

def get_random_lib(call):
    if is_random_numpy_call(call):
        return 'numpy'
    if is_random_torch_call(call):
        return 'torch'
    if is_dataloader_with_shuffle(call):
        return 'torch'
    if is_random_tf_call(call):
        return 'tensorflow'
    if is_random_python_call(call):
        return 'random'
    return None

def is_random_numpy_call(stmt):
    if not isinstance(stmt, ast.Call):
        return False
    if not isinstance(stmt.func, ast.Attribute):
        return False
    return (isinstance(stmt.func.value, ast.Attribute) and
            isinstance(stmt.func.value.value, ast.Name) and
            stmt.func.value.value.id == 'np' and
            stmt.func.value.attr == 'random' and
            stmt.func.attr in {
                'random', 'rand', 'randn', 'randint', 'normal',
                'uniform', 'sample', 'choice', 'shuffle', 'permutation'
            })
def is_random_python_call(stmt):
    if not isinstance(stmt, ast.Call):
        return False
    if not isinstance(stmt.func, ast.Attribute):
        return False
    return (isinstance(stmt.func.value, ast.Name) and
            stmt.func.value.id == 'random' and
            stmt.func.attr in {'randint', 'choice', 'shuffle', 'random', 'uniform'})
def is_random_torch_call(stmt):
    if is_dataloader_with_shuffle(stmt):
       return True
    if not isinstance(stmt, ast.Call):
        return False
    if not isinstance(stmt.func, ast.Attribute):
        return False
    return (isinstance(stmt.func.value, ast.Name) and
            stmt.func.value.id == 'torch' and
            stmt.func.attr in {'rand', 'randn', 'randint', 'random'})
def is_random_tf_call(stmt):
    if not isinstance(stmt, ast.Call):
        return False
    if not isinstance(stmt.func, ast.Attribute):
        return False
    return (isinstance(stmt.func.value, ast.Attribute) and
            isinstance(stmt.func.value.value, ast.Name) and
            stmt.func.value.value.id == 'tf' and
            stmt.func.value.attr == 'random')
def isSklearnRandomAlgo(call):
    if not isinstance(call, ast.Call):
        return False
    if isinstance(call.func, ast.Name):
        return call.func.id in {
            'RandomForestClassifier', 'RandomForestRegressor',
            'KMeans', 'train_test_split', 'RandomizedSearchCV',
            'StratifiedKFold', 'ShuffleSplit', 'GridSearchCV','CatBoostregressor','SGD','Linear'
        }
    return False
def is_dataloader_with_shuffle(stmt):
    if not isinstance(stmt, ast.Call):
        return False
    if isinstance(stmt.func, ast.Name) and stmt.func.id == 'DataLoader':
        for kw in stmt.keywords:
            if kw.arg == 'shuffle' and isinstance(kw.value, ast.Constant) and kw.value.value is True:
                return True
    if isinstance(stmt.func, ast.Attribute) and stmt.func.attr == 'DataLoader':
        for kw in stmt.keywords:
            if kw.arg == 'shuffle' and isinstance(kw.value, ast.Constant) and kw.value.value is True:
                return True
    return False

def hasConstantAndConcatIntersection(block):
    import ast

    TF_INIT_FUNCS = {'Variable', 'ones', 'zeros', 'random_normal', 'random_uniform', 'fill'}
    MODIFICATION_FUNCS = {'concat', 'stack'}

    tf_constant_vars = set()
    ignore_vars = set()
    tensorarray_write_vars = set()

    # Vérifie si le block est dans une boucle (nécessaire pour ce smell)
    def is_inside_loop(node):
        while node:
            if isinstance(node, (ast.For, ast.While)):
                return True
            node = getattr(node, 'parent', None)
        return False

    # 1. Collecte les tf.constant assignés
    for node in ast.walk(block):
        for child in ast.iter_child_nodes(node):
            child.parent = node  # assurer la référence vers parent
        if isinstance(node, ast.Assign) and isinstance(node.value, ast.Call):
            call = node.value
            if (isinstance(call.func, ast.Attribute)
                and hasattr(call.func.value, 'id')
                and call.func.value.id == 'tf'):
                if call.func.attr == 'constant':
                    if len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
                        tf_constant_vars.add(node.targets[0].id)
                elif call.func.attr in TF_INIT_FUNCS:
                    if len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
                        ignore_vars.add(node.targets[0].id)

    # 2. Ignore si uniquement utilisé dans TensorArray.write
    for node in ast.walk(block):
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute):
            if node.func.attr == "write" and isinstance(node.func.value, ast.Name):
                for arg in node.args:
                    if isinstance(arg, ast.Name) and arg.id in tf_constant_vars:
                        tensorarray_write_vars.add(arg.id)

    # 3. Détecte les modifications suspectes dans le bloc, UNIQUEMENT DANS UNE BOUCLE
    for node in ast.walk(block):
        # Assurer parent
        for child in ast.iter_child_nodes(node):
            child.parent = node

        # tf.concat / tf.stack
        if isinstance(node, ast.Assign) and isinstance(node.value, ast.Call):
            call = node.value
            if (isinstance(call.func, ast.Attribute)
                and hasattr(call.func.value, 'id')
                and call.func.value.id == 'tf'
                and call.func.attr in MODIFICATION_FUNCS
                and is_inside_loop(node)):  # Seulement dans boucle
                involved_vars = set()
                for arg in call.args:
                    if isinstance(arg, ast.List):
                        involved_vars |= set(elt.id for elt in arg.elts if isinstance(elt, ast.Name))
                    elif isinstance(arg, ast.Name):
                        involved_vars.add(arg.id)
                smell_vars = (involved_vars & tf_constant_vars) - ignore_vars - tensorarray_write_vars
                if smell_vars:
                    return True

        # Opération arithmétique (+, *, etc.) sur tf.constant dans une boucle seulement
        if isinstance(node, ast.Assign) and isinstance(node.value, ast.BinOp) and is_inside_loop(node):
            for side in [node.value.left, node.value.right]:
                if isinstance(side, ast.Name):
                    if side.id in tf_constant_vars and side.id not in ignore_vars and side.id not in tensorarray_write_vars:
                        return True

    # Aucune intersection détectée
    return False
def isMLMethodCall(call):
    if not isinstance(call, ast.Call):
        return False
    if isinstance(call.func, ast.Name):
        func_name = call.func.id
    elif isinstance(call.func, ast.Attribute):
        func_name = call.func.attr
    else:
        return False
    hyperparameter_functions = {
        # Scikit-Learn
        'KMeans', 'DBSCAN', 'AgglomerativeClustering',
        'RandomForestClassifier','RandomForestRegressor', 'GradientBoostingClassifier', 'AdaBoostClassifier',
        'LogisticRegression', 'LinearRegression', 'Lasso', 'Ridge',
        'SVC', 'SVR', 'DecisionTreeClassifier', 'DecisionTreeRegressor',
        'MLPClassifier', 'MLPRegressor',
        # PyTorch Optimizers
        'SGD', 'Adagrad', 'Adadelta', 'Adamax', 'RMSprop', 'Net',
        # TensorFlow Optimizers (et éventuellement des layers si pertinent)
        'Adam', 'Ftrl', 'Nadam', 'Adamax', 'Dense', 'Conv2D', 'LSTM',
        # XGBoost
        'XGBClassifier', 'XGBRegressor',
        # LightGBM
        'LGBMClassifier', 'LGBMRegressor'
        # LightGBM
        'Sequential'
    }
    return func_name in hyperparameter_functions
def hasExplicitHyperparameters(call):
    return len(call.keywords) > 0

def isLog(call):
    if not isinstance(call, ast.Call):
        return False
    if not isinstance(call.func, ast.Attribute):
        return False
    if hasattr(call.func.value, 'id') and call.func.value.id == 'tf' and call.func.attr == 'log':
        return True
    return False

def hasMask(call):
    if not isinstance(call, ast.Call):
        return False
    if not isLog(call):
        return False
    if len(call.args) == 0:
        return False
    arg = call.args[0]
    if isinstance(arg, ast.Call) and isinstance(arg.func, ast.Attribute):
        if hasattr(arg.func.value, 'id') and arg.func.value.id == 'tf' and arg.func.attr == 'clip_by_value':
            return True
    return False

def isForwardCall(call):
    if not isinstance(call, ast.Call):
        return False
    if not isinstance(call.func, ast.Attribute):
        return False
    if call.func.attr != 'forward':
        return False

    # (NOUVEAU) Vérifie si on est dans la méthode __call__ d'une classe : autorisé => pas de smell
    node = call
    while hasattr(node, 'parent') and node.parent is not None:
        node = node.parent
        if isinstance(node, ast.FunctionDef) and node.name == '__call__':
            return False  # Autorisé dans __call__, donc pas un smell

    # Base de l'appel (self, self.block, model, etc)
    base = call.func.value
    while isinstance(base, ast.Attribute):
        base = base.value

    if isinstance(base, ast.Name):
        base_id = base.id
        # Accepte self
        if base_id == 'self':
            return True
        # Accepte toute variable qui ressemble à un modèle dans le code
        root = call
        while hasattr(root, 'parent') and root.parent is not None:
            root = root.parent
        for node in ast.walk(root):
            if isinstance(node, ast.Assign) and len(node.targets) == 1:
                target = node.targets[0]
                if isinstance(target, ast.Name) and target.id == base_id:
                    val = node.value
                    if isinstance(val, ast.Call):
                        func = val.func
                        if isinstance(func, ast.Attribute):
                            if (
                                (isinstance(func.value, ast.Name) and func.value.id in {'torch', 'nn'})
                                or (isinstance(func.value, ast.Attribute) and func.value.attr == 'nn')
                            ):
                                return True
    # Optionnel : flag toute utilisation de .forward dans un fichier PyTorch
    root = call
    while hasattr(root, 'parent') and root.parent is not None:
        root = root.parent
    torch_present = False
    for node in ast.walk(root):
        if isinstance(node, ast.Import):
            for alias in node.names:
                if alias.name == 'torch':
                    torch_present = True
        elif isinstance(node, ast.ImportFrom):
            if node.module and node.module.startswith('torch'):
                torch_present = True
    if torch_present:
        return True
    return False

def isRelevantLibraryCall(node):
    if not isinstance(node, ast.Call):
       return False
    base = get_base_name(node.func)
    return base in ['torch', 'numpy', 'random', 'transformers']

def hasManualSeed(node):
    if not isinstance(node, ast.Call):
        return False
    if isinstance(node.func, ast.Attribute) and node.func.attr in {'manual_seed', 'set_seed', 'seed'}:
        return bool(node.args and isinstance(node.args[0], ast.Constant))
    return False

def isEvalCall(node):
    return (isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == 'eval')

def isTrainCall(node):
    return (isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == 'train')

def isOptimizerStep(node):
    return (
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and getattr(node.func.value, 'id', None) == 'optimizer'
        and node.func.attr == 'step'
    )

train_lines = []

def init_train_lines(ast_node):
    global train_lines
    train_lines = []
    for stmt in ast.walk(ast_node):
        if (isTrainCall(stmt) or isOptimizerStep(stmt)) and hasattr(stmt, 'lineno'):
            train_lines.append(stmt.lineno)
    train_lines.sort()

def hasLaterTrainCall(node):
    if not hasattr(node, 'lineno'):
        return False

    node_line = node.lineno
    for tline in train_lines:
        if tline > node_line:
            return True
    return False

def isLossBackward(node):
    if not isinstance(node, ast.Call):
        return False
    if not isinstance(node.func, ast.Attribute):
        return False
    return node.func.attr == 'backward'

def isZeroGradCall(node):
    if not isinstance(node, ast.Call):
        return False
    if not isinstance(node.func, ast.Attribute):
        return False
    return node.func.attr == 'zero_grad'

def isClearGradCall(node):
    if not isinstance(node, ast.Call):
        return False
    if not isinstance(node.func, ast.Attribute):
        return False
    return node.func.attr == 'clear_grad'

def isPaddleEnvironment(root_node):
    """
    Détecte si l'AST contient un import de paddle,
    donc si l'on est dans un environnement Paddle.
    """
    import ast
    for stmt in ast.walk(root_node):
        # Case: import paddle
        if isinstance(stmt, ast.Import):
            for alias in stmt.names:
                if alias.name == 'paddle':
                    return True
        # Case: from paddle import <X>
        if isinstance(stmt, ast.ImportFrom):
            if stmt.module and 'paddle' in stmt.module:
                return True
    return False

def isInsideNoGrad(node):
    """
    Retourne True si le noeud se trouve dans un bloc `with torch.no_grad():`
    """
    current = node
    while getattr(current, 'parent', None) is not None:
        current = current.parent
        if isinstance(current, ast.With):
            for item in current.items:
                if isinstance(item.context_expr, ast.Call):
                    called = item.context_expr.func
                    if (isinstance(called, ast.Attribute)
                        and isinstance(called.value, ast.Name)
                        and called.value.id == 'torch'
                        and called.attr == 'no_grad'):
                        return True
    return False

def hasPrecedingZeroGrad(call):
    """
    Vérifie si backward() est précédé d'un appel zero_grad(), ou si on est
    dans un bloc no_grad(), ou si on est en environnement Paddle et qu'un
    clear_grad() (paddle) est présent *après* la ligne du backward.
    """
    import ast
    if isInsideNoGrad(call):
        return True

    if not hasattr(call, 'lineno'):
        return False
    node_line = call.lineno

    root_node = call
    while getattr(root_node, 'parent', None) is not None:
        root_node = root_node.parent

    if not isPaddleEnvironment(root_node):
        for stmt in ast.walk(root_node):
            if isZeroGradCall(stmt) and hasattr(stmt, 'lineno'):
                if stmt.lineno < node_line:
                    return True
        return False
    else:

        for stmt in ast.walk(root_node):
            if isZeroGradCall(stmt) and hasattr(stmt, 'lineno'):
                if stmt.lineno < node_line:
                    return True

        for stmt in ast.walk(root_node):
            if isClearGradCall(stmt) and hasattr(stmt, 'lineno'):
                if stmt.lineno > node_line:
                    return True

        return False

tracked_tensors = set()

pytorch_tensors = set()

def isPytorchTensorDefinition(node):
    """Register variables created via torch tensor creation functions."""
    global pytorch_tensors
    if isinstance(node, ast.Assign) and isinstance(node.value, ast.Call):
        call = node.value
        if isinstance(call.func, ast.Attribute):
            # Catch torch.tensor(...) and torch.Tensor(...)
            if isinstance(call.func.value, ast.Name) and call.func.value.id == 'torch':
                if call.func.attr in {'tensor', 'Tensor'}:
                    for target in node.targets:
                        if isinstance(target, ast.Name):
                            pytorch_tensors.add(target.id)
                            return True
    return False

def isPytorchTensorUsage(node):
    # Limite la détection aux variables connues comme tenseurs torch
    if not isinstance(node, ast.Call): return False
    if not isinstance(node.func, ast.Attribute): return False
    ops = {'matmul', 'add', 'mul', 'sub', 'div', 'mm'}
    if node.func.attr not in ops: return False
    if isinstance(node.func.value, ast.Name):
        var_name = node.func.value.id
        return var_name in pytorch_tensors
    return False



def isModelCreation(node):
    """
    Détecte la création d'un modèle ou d'une couche (Keras/PyTorch) même sans assignation explicite,
    y compris passé comme argument à une fonction/méthode (ex: append(Model(...))).
    """
    if not isinstance(node, ast.Call):
        return False
    # Liste élargie : tous les objets potentiellement coûteux en mémoire
    model_layer_names = {
        "Sequential", "Model",
        "Conv1D", "Conv2D", "Conv3D", "Dense", "LSTM", "GRU", "RNN",
        "LeakyReLU", "ReLU", "MaxPooling2D", "Flatten", "Dropout"
    }
    # Cas appel direct (ex: Sequential(...), Model(...))
    if isinstance(node.func, ast.Name) and node.func.id in model_layer_names:
        return True
    # Cas appel qualifié (ex: tf.keras.Sequential(...), tf.keras.Model(...))
    if isinstance(node.func, ast.Attribute) and node.func.attr in model_layer_names:
        return True
    return False


def isMemoryFreeCall(node):
    """Return True if the node represents a memory-freeing API call."""
    if isinstance(node, ast.Call):
        # Handle method calls like tensor.detach() or backend.clear_session()
        if isinstance(node.func, ast.Attribute):
            # PyTorch: tensor.detach()
            if node.func.attr == 'detach':
                return True
            # TensorFlow/Keras: clear_session() called as an attribute
            if node.func.attr == 'clear_session':
                # Check if this call is inside a loop (for memory freeing in loops)
                current = node
                in_loop = False
                while hasattr(current, "parent") and current.parent is not None:
                    if isinstance(current.parent, ast.For):
                        in_loop = True
                        break
                    current = current.parent
                return in_loop  # True if in a loop, False otherwise
        # Handle function calls like clear_session() imported directly
        elif isinstance(node.func, ast.Name):
            if node.func.id == 'clear_session':
                # Similar loop check for standalone clear_session()
                current = node
                in_loop = False
                while hasattr(current, "parent") and current.parent is not None:
                    if isinstance(current.parent, ast.For):
                        in_loop = True
                        break
                    current = current.parent
                return in_loop
    # Handle explicit deletions: `del var`
    if isinstance(node, ast.Delete):
        return True
    # Handle assigning a variable to None as a form of manual cleanup
    if isinstance(node, ast.Assign) and len(node.targets) == 1:
        target = node.targets[0]
        if (isinstance(target, ast.Name) 
                and isinstance(node.value, ast.Constant) 
                and node.value.value is None):
            return True
    return False


def isFitTransform(node):
    return (
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == 'fit_transform'
    )

def pipelineUsed(node):
    current = node
    while current:
        if isinstance(current, ast.Call):
            if isinstance(current.func, ast.Name) and current.func.id in {'make_pipeline', 'Pipeline'}:
                return True
            if isinstance(current.func, ast.Attribute) and current.func.attr in {'fit', 'predict'}:
                base = get_base_name(current.func.value)
                if base in {'pipeline', 'clf'}:
                    return True
        current = getattr(current, 'parent', None)
    return False

def usedBeforeTrainTestSplit(node):
    if not hasattr(node, 'lineno'):
        return False
    fit_line = node.lineno
    root = node
    while getattr(root, 'parent', None):
        root = root.parent
    for sub in ast.walk(root):
        if isinstance(sub, ast.Call):
            if isinstance(sub.func, ast.Name) and sub.func.id == 'train_test_split':
                if hasattr(sub, 'lineno') and sub.lineno > fit_line:
                    return True
    return False

def pipelineUsedGlobally(ast_node):
    for node in ast.walk(ast_node):
        if isinstance(node, ast.Call):
            if isinstance(node.func, ast.Name) and node.func.id in {'Pipeline', 'make_pipeline'}:
                return True
    return False

def isModelFitPresent(ast_node):
    for node in ast.walk(ast_node):
        if isinstance(node, ast.Call):
            if isinstance(node.func, ast.Attribute) and node.func.attr == "fit":
                return True
    return False

def isFitCall(node):
   return isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == "fit"

def reportFitLine(msg, node):   report_with_line(msg, node)

def report_line(message, node):
   report_with_line(message, node)

def hasEarlyStoppingCallback(call):
   # return True if 'callbacks' exists and contains 'EarlyStopping'
   if not (isinstance(call, ast.Call) and hasKeyword(call, "callbacks")):
       return False
   for kw in call.keywords:
       if kw.arg == "callbacks" and "EarlyStopping" in ast.unparse(kw.value):
           return True
   return False

def rule_R19(ast_node):
    import ast
    add_parent_info(ast_node)
    #set_deterministic_flag(ast_node)
    # "Threshold Validation Metrics Count"
    variable_ops = gather_scale_sensitive_ops(ast_node)
    scaled_vars = gather_scaled_vars(ast_node)
    if ((((count_dep := sum(1 for m in ast.walk(ast_node) if (isMetricCall(m) and isThresholdDependent(m))))) and ((count_indep := sum(1 for m in ast.walk(ast_node) if (isMetricCall(m) and isThresholdIndependent(m)))))) and ((count_indep <= count_dep))):
        report("Too many threshold-dependent metrics")
