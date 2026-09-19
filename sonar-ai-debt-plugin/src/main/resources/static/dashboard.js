window.registerExtension('aidebt/dashboard', function (options) {
  const root = options.el;
  const sourceCache = new Map();
  const fitRootToViewport = () => {
    const left = root.getBoundingClientRect().left;
    const viewportWidth = document.documentElement.clientWidth;
    root.style.width = `${Math.max(320, viewportWidth - left)}px`;
    root.style.maxWidth = 'none';
  };
  fitRootToViewport();
  window.addEventListener('resize', fitRootToViewport);
  const issueRules = [
    'broad-except','mutable-default','wildcard-import','debug-output','unsafe-eval','placeholder',
    'swallowed-exception','training-on-evaluation-data','hardcoded-secret','opaque-ml-config',
    'missing-random-seed','unpinned-model-revision','context-switch','redundant-logic',
    'semantic-name-inconsistency','explanation-gap','multiply-nested-container','long-parameter-list',
    'long-method','long-lambda','long-ternary','complex-comprehension','long-message-chain','large-class'
  ];
  const specDetectRules = Array.from({length: 24}, (_, index) => `specdetect-r${index + 1}`)
    .concat('specdetect-r11bis');
  issueRules.push(...specDetectRules);
  const specDetectTitles = {
    'r1':'Broadcasting feature not used',
    'r2':'Random seed not set',
    'r3':'TensorArray not used',
    'r4':'Training/evaluation mode improper toggling',
    'r5':'Hyperparameter not explicitly set',
    'r6':'Deterministic algorithm option not used',
    'r7':'Missing mask of invalid value',
    'r8':'PyTorch call method misused',
    'r9':'Gradients not cleared before backpropagation',
    'r10':'Memory not freed',
    'r11':'Data leakage before train/test split',
    'r11bis':'Data leakage without pipeline',
    'r12':'Matrix multiplication API misused',
    'r13':'Empty column misinitialization',
    'r14':'DataFrame conversion API misused',
    'r15':'Merge API parameter not explicitly set',
    'r16':'API result discarded or not applied in place',
    'r17':'Unnecessary iteration',
    'r18':'NaN comparison',
    'r19':'Threshold validation metrics count',
    'r20':'Chained indexing on DataFrames',
    'r21':'Columns and data types not explicitly set in DataFrame read',
    'r22':'No scaling before scale-sensitive operation',
    'r23':'EarlyStopping not used in model.fit',
    'r24':'Index column not explicitly set in DataFrame read'
  };
  const ruleIds = {
    'AIDEBT-PY-001':'mutable-default','AIDEBT-PY-002':'broad-except',
    'AIDEBT-PY-003':'swallowed-exception','AIDEBT-PY-004':'wildcard-import',
    'AIDEBT-PY-005':'debug-output','AIDEBT-PY-006':'unsafe-eval','AIDEBT-PY-007':'placeholder',
    'AIDEBT-PY-008':'hardcoded-secret','AIDEBT-PY-009':'training-on-evaluation-data',
    'AIDEBT-PY-010':'opaque-ml-config','AIDEBT-PY-011':'missing-random-seed',
    'AIDEBT-PY-012':'unpinned-model-revision','AIDEBT-PY-013':'context-switch',
    'AIDEBT-PY-014':'redundant-logic','AIDEBT-PY-015':'semantic-name-inconsistency',
    'AIDEBT-PY-016':'explanation-gap'
    ,'AIDEBT-PY-017':'multiply-nested-container','AIDEBT-PY-018':'long-parameter-list',
    'AIDEBT-PY-019':'long-method','AIDEBT-PY-020':'long-lambda','AIDEBT-PY-021':'long-ternary',
    'AIDEBT-PY-022':'complex-comprehension','AIDEBT-PY-023':'long-message-chain',
    'AIDEBT-PY-024':'large-class','AIDEBT-PY-025':'coupling-cycle',
    'AIDEBT-PY-026':'unstable-dependency-direction'
  };
  root.classList.add('aidebt-scroll-root');
  const metricKeys = [
    'aidebt_adsi','aidebt_tdsi','aidebt_cogdi','aidebt_metric_coverage','aidebt_effort_model',
    'aidebt_aisd','aidebt_aisd_score','aidebt_cii','aidebt_cii_evidence','aidebt_cdi','aidebt_cdi_score','aidebt_cdi_evidence','aidebt_hts','aidebt_hts_evidence','aidebt_csd_evidence','aidebt_rlr_evidence','aidebt_sii_evidence','aidebt_egr_evidence',
    'aidebt_csd','aidebt_rlr','aidebt_sii','aidebt_egr','aidebt_files','aidebt_logical_lines','aidebt_blocks',
    'aidebt_aisd_smells','aidebt_aisd_kloc','aidebt_smell_broad_except','aidebt_smell_mutable_default',
    'aidebt_smell_wildcard_import','aidebt_smell_debug_output','aidebt_smell_unsafe_eval',
    'aidebt_smell_placeholder','aidebt_smell_swallowed_exception','aidebt_smell_evaluation_leakage',
    'aidebt_smell_hardcoded_secret',
    'aidebt_cii_ca','aidebt_cii_ce','aidebt_cii_coupled_files','aidebt_cdi_mean_complexity',
    'aidebt_cii_internal_dependencies','aidebt_cii_external_dependencies','aidebt_cii_cycles',
    'aidebt_cii_stability_violations','aidebt_cii_remediation_actions',
    'aidebt_cdi_comment_density','aidebt_cdi_comment_lines','aidebt_cdi_source_lines','aidebt_cdi_blocks',
    'aidebt_cdi_mean_nesting','aidebt_cdi_documented_blocks','aidebt_cdi_documentation_coverage',
    'aidebt_cdi_total_complexity_excess','aidebt_cdi_undocumented_complexity_excess',
    'aidebt_hts_implicit','aidebt_hts_explicit','aidebt_hts_config_driven','aidebt_hts_total',
    'aidebt_hts_opaque_config','aidebt_hts_missing_seed','aidebt_hts_unpinned_revision',
    'aidebt_csd_switches','aidebt_csd_transitions','aidebt_csd_mean_similarity',
    'aidebt_csd_minimum_similarity','aidebt_csd_threshold','aidebt_rlr_redundant','aidebt_rlr_pairs',
    'aidebt_rlr_maximum_syntactic_similarity','aidebt_rlr_maximum_semantic_similarity',
    'aidebt_rlr_syntactic_threshold','aidebt_rlr_semantic_threshold','aidebt_sii_inconsistent',
    'aidebt_sii_pairs','aidebt_sii_identifiers','aidebt_sii_semantically_similar','aidebt_sii_maximum_semantic_similarity',
    'aidebt_sii_maximum_context_similarity','aidebt_sii_minimum_lexical_among_similar',
    'aidebt_sii_semantic_threshold','aidebt_sii_context_threshold','aidebt_sii_lexical_threshold',
    'aidebt_egr_unexplained','aidebt_egr_complex','aidebt_egr_cc_triggered',
    'aidebt_egr_nesting_triggered','aidebt_egr_control_flow_triggered','aidebt_egr_cc_threshold',
    'aidebt_egr_nesting_threshold','aidebt_weight_aisd','aidebt_weight_cii',
    'aidebt_weight_cdi','aidebt_weight_hts','aidebt_weight_csd','aidebt_weight_rlr',
    'aidebt_weight_sii','aidebt_weight_egr','aidebt_weight_tdsi','aidebt_weight_cogdi'
  ];

  root.innerHTML = '<div class="aidebt-loading">Loading the diagnostic report…</div>';
  const component = encodeURIComponent(options.component.key);
  const measuresRequest = fetch(`${window.baseUrl || ''}/api/measures/component?component=${component}&metricKeys=${metricKeys.join(',')}`)
    .then(response => { if (!response.ok) throw new Error(`SonarQube returned ${response.status}`); return response.json(); });
  const issuesRequest = fetch(`${window.baseUrl || ''}/api/issues/search?componentKeys=${component}&ps=500`)
    .then(response => response.ok ? response.json() : {issues: []})
    .then(payload => ({issues: (payload.issues || []).filter(issue => /(?:AIDEBT-PY-|SPECDETECT4AI-)/.test(issue.message || ''))}))
    .catch(() => ({issues: []}));
  Promise.all([measuresRequest, issuesRequest])
    .then(([payload, issuePayload]) => render(payload.component.measures || [], issuePayload.issues || []))
    .catch(error => showError(error.message));

  function render(measures, issues) {
    const v = Object.fromEntries(measures.map(measure => [measure.metric,
      measure.metric.endsWith('_evidence') || measure.metric === 'aidebt_effort_model'
        ? measure.value : Number(measure.value)]));
    const effort = parseJson(v.aidebt_effort_model);
    const technical = metricDefinitions(v).slice(0, 4);
    const cognitive = metricDefinitions(v).slice(4);
    technical.concat(cognitive).forEach(metric => {
      metric.effort = effort.metrics && effort.metrics[metric.short] ? effort.metrics[metric.short] : null;
    });
    const tdCalculation = calculateIndex('TDSI', technical, v.aidebt_tdsi);
    const cogCalculation = calculateIndex('CogDI', cognitive, v.aidebt_cogdi);
    const finalCalculation = calculateFinal(v, tdCalculation, cogCalculation);
    root.innerHTML = '';
    const page = el('main', 'aidebt-page');
    page.append(header(v), summary(v, technical, cognitive, effort),
      overview(technical, cognitive, v, issues), calculationSection(tdCalculation, cogCalculation, finalCalculation));
    root.appendChild(page);
  }

  function header(v) {
    const section = el('header', 'aidebt-hero');
    const copy = el('div', 'aidebt-hero-copy');
    copy.append(el('span', 'aidebt-eyebrow', 'TECHNICAL AND COGNITIVE DEBT ASSESSMENT'),
      el('h1', '', 'AI Debt Metrics for Python'),
      el('p', '', 'An evidence-led view of maintainability, reproducibility, structure, and cognitive burden in the analyzed codebase.'));
    const scope = el('div', 'aidebt-scope');
    scope.append(scopeItem(integer(v.aidebt_files), 'files'), scopeItem(integer(v.aidebt_logical_lines), 'logical lines'),
      scopeItem(integer(v.aidebt_blocks), 'callable blocks'));
    const meta = el('div', 'aidebt-hero-meta');
    meta.append(scope, effortConvention());
    copy.appendChild(meta);
    section.appendChild(copy);
    return section;
  }

  function summary(v, technical, cognitive, effort) {
    const section = el('section', 'aidebt-summary');
    section.setAttribute('aria-label', 'Index summary');
    section.append(scoreCard('ADSI', v.aidebt_adsi, 'Overall debt indicator', false, false, effort.overall),
      scoreCard('TDSI', v.aidebt_tdsi, 'Technical debt profile', false, false, effort.technical),
      scoreCard('CogDI', v.aidebt_cogdi, 'Cognitive debt profile', false, false, effort.cognitive),
      coverageCard(v.aidebt_metric_coverage, technical.concat(cognitive)));
    return section;
  }

  function scoreCard(label, value, caption, primary, benefit, effort) {
    const card = el('article', `aidebt-score-card${primary ? ' aidebt-score-primary' : ''}`);
    const riskValue = benefit && isNumber(value) ? 1 - value : value;
    const top = el('div', 'aidebt-score-top');
    top.append(el('span', '', label), benefit ? benefitBadge(value) : riskBadge(value));
    const gauge = el('div', 'aidebt-gauge');
    gauge.style.setProperty('--ad-score-color', riskColor(riskValue));
    gauge.style.setProperty('--score', `${Math.max(0, Math.min(1, value || 0)) * 360}deg`);
    gauge.appendChild(el('strong', '', isNumber(value) ? fixed(value) : 'N/A'));
    card.append(top, gauge, el('small', '', caption));
    if (effort) card.appendChild(effortLabel(effort, label));
    return card;
  }

  function coverageCard(value, metrics) {
    const fraction = isNumber(value) ? value / 100 : NaN;
    const card = el('article', 'aidebt-score-card');
    const top = el('div', 'aidebt-score-top');
    top.append(el('span', '', 'Metric availability'), el('span', 'aidebt-badge aidebt-neutral', 'ANALYSIS INPUT'));
    const gauge = el('div', 'aidebt-gauge aidebt-coverage-gauge');
    gauge.style.setProperty('--score', `${Math.max(0, Math.min(1, fraction || 0)) * 360}deg`);
    gauge.appendChild(el('strong', '', isNumber(value) ? `${value.toFixed(1)}%` : 'N/A'));
    const available = metrics.filter(metric => isNumber(metric.score)).length;
    card.append(top, gauge, el('small', 'aidebt-multiline',
      `${available} of ${metrics.length} weighted signals contributed.\n100% means none was N/A.`));
    return card;
  }

  function overview(technical, cognitive, values, issues) {
    const section = el('section', 'aidebt-overview');
    section.append(profile('Technical Debt Profile', technical, values, issues),
      profile('Cognitive Debt Profile', cognitive, values, issues));
    return section;
  }

  function profile(title, metrics, values, issues) {
    const panel = el('article', 'aidebt-panel');
    panel.append(el('h2', 'aidebt-profile-title', title),
      el('p', 'aidebt-panel-intro', title.startsWith('Technical')
        ? 'Reproducibility, dependency structure, documentation, and AI-associated implementation risks.'
        : 'Signals that increase the effort required to understand, verify, and maintain the code.'));
    metrics.forEach(metric => {
      const row = el('div', 'aidebt-profile-row');
      const label = el('button', 'aidebt-profile-label');
      label.type = 'button';
      label.setAttribute('aria-label', `${metric.short}: ${metric.name}`);
      label.dataset.fullName = metric.name;
      label.append(el('strong', '', metric.short));
      label.addEventListener('click', () => renderMetricPage(metric, values, issues));
      const track = el('div', 'aidebt-track');
      const fill = el('div', `aidebt-fill ${riskClass(metric.score)}`);
      fill.style.width = `${isNumber(metric.displayScore) ? Math.max(0, Math.min(1, metric.displayScore)) * 100 : 0}%`;
      track.appendChild(fill);
      row.append(label, track, el('code', '', isNumber(metric.displayScore) ? fixed(metric.displayScore) : 'N/A'));
      panel.appendChild(row);
    });
    const legend = el('div', 'aidebt-risk-legend');
    legend.append(legendItem('aidebt-low', 'Low'), legendItem('aidebt-medium', 'Moderate'),
      legendItem('aidebt-high', 'High'));
    panel.appendChild(legend);
    return panel;
  }

  function legendItem(className, label) {
    const item = el('span', '');
    item.append(el('i', className), document.createTextNode(label));
    return item;
  }

  function calculationSection(td, cog, finalCalculation) {
    const section = el('section', 'aidebt-calculations');
    section.appendChild(sectionTitle('Contribution to index scores', 'Each bar uses the contribution value on the same 0–1 scale as its index'));
    const grid = el('div', 'aidebt-calculation-grid');
    grid.append(calculationCard(td), calculationCard(cog), finalCard(finalCalculation));
    section.appendChild(grid);
    return section;
  }

  function calculationCard(calculation) {
    const card = el('article', 'aidebt-formula-card');
    const heading = el('div', 'aidebt-formula-heading');
    heading.append(el('div', '', calculation.name), el('strong', '', fixed(calculation.result)));
    card.append(heading);
    card.appendChild(contributionList(calculation.terms));
    return card;
  }

  function contributionList(terms) {
    const list = el('div', 'aidebt-contributions');
    terms.forEach(term => {
      const row = el('div', 'aidebt-contribution-row');
      const label = el('span', '', term.applicable ? term.short : `${term.short} · N/A`);
      const detail = term.applicable
        ? `Contribution ${fixed(term.contribution)}`
        : `Not included: ${term.naReason}`;
      row.append(label, el('span', '', detail));
      if (term.applicable) {
        const track = el('div', 'aidebt-contribution-track');
        const fill = el('div', 'aidebt-contribution-fill');
        fill.style.width = `${Math.min(100, term.contribution * 100)}%`;
        track.title = `${fixed(term.contribution)} contribution on the 0–1 index scale`;
        track.appendChild(fill);
        row.appendChild(track);
      }
      list.appendChild(row);
    });
    return list;
  }

  function finalCard(calculation) {
    const card = el('article', 'aidebt-formula-card aidebt-final-card');
    const heading = el('div', 'aidebt-formula-heading');
    heading.append(el('div', '', 'ADSI'), el('strong', '', fixed(calculation.result)));
    card.append(el('p', 'aidebt-index-description', 'ADSI combines the available technical and cognitive profiles into the overall debt indicator.'),
      heading, contributionList(calculation.terms), el('p', 'aidebt-formula-note', calculation.note));
    return card;
  }

  function smellSection(v) {
    const smells = [
      ['Debug output', v.aidebt_smell_debug_output], ['Broad except', v.aidebt_smell_broad_except],
      ['Mutable default', v.aidebt_smell_mutable_default], ['Wildcard import', v.aidebt_smell_wildcard_import],
      ['Unsafe eval / exec', v.aidebt_smell_unsafe_eval], ['Placeholder', v.aidebt_smell_placeholder],
      ['Swallowed exception', v.aidebt_smell_swallowed_exception],
      ['Evaluation leakage', v.aidebt_smell_evaluation_leakage],
      ['Opaque ML config', v.aidebt_hts_opaque_config], ['Missing random seed', v.aidebt_hts_missing_seed],
      ['Unpinned model revision', v.aidebt_hts_unpinned_revision],
      ['Hard-coded secret', v.aidebt_smell_hardcoded_secret]
    ];
    const max = Math.max(1, ...smells.map(item => item[1] || 0));
    const section = el('section', 'aidebt-smells');
    section.appendChild(sectionTitle('Detected AI-associated smell candidates',
      `${integer(v.aidebt_aisd_smells)} unique instances across ${number(v.aidebt_aisd_kloc, 3)} KLOC`));
    const layout = el('div', 'aidebt-smell-layout');
    const chart = el('div', 'aidebt-smell-chart');
    smells.forEach(([name, count]) => {
      const row = el('div', 'aidebt-smell-row');
      const track = el('div', 'aidebt-smell-track');
      const fill = el('div', 'aidebt-smell-fill');
      fill.style.width = `${((count || 0) / max) * 100}%`;
      track.appendChild(fill);
      row.append(el('span', '', name), track, el('strong', '', integer(count)));
      chart.appendChild(row);
    });
    const result = el('aside', 'aidebt-raw-callout');
    result.append(el('span', '', 'Published AISD result'), el('strong', '', number(v.aidebt_aisd_score, 3)),
      el('p', '', 'The exact density and normalization equations are intentionally omitted from the public dashboard.'));
    layout.append(chart, result);
    section.appendChild(layout);
    return section;
  }

  function detailsSection(title, metrics, values, issues) {
    const section = el('section', 'aidebt-details-section');
    section.appendChild(sectionTitle(title, 'Open a metric to inspect its raw evidence and transformation'));
    const grid = el('div', 'aidebt-details-grid');
    metrics.forEach(metric => grid.appendChild(metricDetail(metric, values, issues)));
    section.appendChild(grid);
    return section;
  }

  function metricDetail(metric, values, issues) {
    const detail = el('details', 'aidebt-metric-detail');
    detail.open = true;
    const summary = el('summary', '');
    const identity = el('div', 'aidebt-detail-identity');
    identity.append(el('span', 'aidebt-metric-symbol', metric.short), el('div', '', metric.name));
    const action = el('span', 'aidebt-detail-action', 'Collapse');
    summary.append(identity, el('strong', riskClass(metric.score), isNumber(metric.displayScore) ? fixed(metric.displayScore) : 'N/A'), action);
    const body = el('div', 'aidebt-detail-body');
    body.append(el('p', 'aidebt-interpretation', isNumber(metric.score) ? metric.interpretation : metric.naReason),
      evidenceGrid(metric.evidence));
    const open = el('button', 'aidebt-open-metric', `Open ${metric.short} analysis`);
    open.type = 'button';
    open.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      renderMetricPage(metric, values, issues);
    });
    body.appendChild(open);
    detail.append(summary, body);
    detail.addEventListener('toggle', () => {
      action.textContent = detail.open ? 'Collapse' : 'Expand';
      action.setAttribute('aria-label', detail.open ? 'Collapse metric details' : 'Expand metric details');
    });
    return detail;
  }

  function renderMetricPage(metric, values, issues) {
    root.innerHTML = '';
    const page = el('main', 'aidebt-page aidebt-metric-page');
    const back = el('button', 'aidebt-back', '← Back to overview');
    back.type = 'button';
    back.addEventListener('click', () => render(Object.entries(values).map(([metric, value]) => ({metric, value})), issues));
    const hero = el('header', 'aidebt-metric-hero');
    const identity = el('div', '');
    identity.append(el('span', 'aidebt-eyebrow', `${metric.short} · AI-CODE DEBT SIGNAL`),
      el('h1', '', metric.name), el('p', '', metric.description));
    const context = el('div', 'aidebt-metric-context');
    context.append(contextItem('What it measures', metric.measures), contextItem('How to read it', metric.interpretation));
    identity.appendChild(context);
    hero.append(identity, scoreCard(metric.short, metric.displayScore,
      metric.benefit ? 'Transparency benefit score' : 'Normalized debt score', false, metric.benefit, metric.effort));
    const explanation = el('section', 'aidebt-metric-explanation');
    const calculatedEvidence = metric.evidence.concat(thresholdEvidence(metric.short, values));
    explanation.append(sectionTitle('Calculated evidence', 'Observed inputs, diagnostics, and active thresholds'),
      evidenceGrid(calculatedEvidence));
    const related = issues.filter(issue => rulesForMetric(metric.short).includes(ruleForIssue(issue)));
    const evidenceSection = metric.short === 'CII' ? ciiEvidenceSection(values)
      : metric.short === 'CDI' ? cdiEvidenceSection(values)
      : metric.short === 'HTS' ? htsEvidenceSection(values)
      : metric.short === 'CSD' ? csdEvidenceSection(values)
      : metric.short === 'RLR' ? rlrEvidenceSection(values)
      : metric.short === 'SII' ? siiEvidenceSection(values)
      : metric.short === 'EGR' ? egrEvidenceSection(values)
      : findingList(metric.short, related);
    page.append(back, hero, explanation, evidenceSection);
    root.appendChild(page);
    requestAnimationFrame(() => {
      fitRootToViewport();
      root.scrollTop = 0;
      root.scrollLeft = 0;
      if (typeof root.scrollTo === 'function') root.scrollTo({top: 0, left: 0, behavior: 'auto'});
      page.scrollIntoView({block: 'start', inline: 'nearest'});
    });
  }

  function contextItem(title, copy) {
    const item = el('div', '');
    item.append(el('span', '', title), el('p', '', copy));
    return item;
  }

  function thresholdEvidence(short, v) {
    const rows = {
      CSD: [['Mean adjacent similarity', v.aidebt_csd_mean_similarity], ['Minimum similarity', v.aidebt_csd_minimum_similarity], ['Switch boundary', v.aidebt_csd_threshold]],
      RLR: [['Maximum syntax similarity', v.aidebt_rlr_maximum_syntactic_similarity], ['Syntax-similarity boundary', v.aidebt_rlr_syntactic_threshold], ['Maximum behavioral similarity', v.aidebt_rlr_maximum_semantic_similarity], ['Behavioral-similarity boundary', v.aidebt_rlr_semantic_threshold]],
      SII: [['Concept-and-context matches', v.aidebt_sii_semantically_similar], ['Maximum concept similarity', v.aidebt_sii_maximum_semantic_similarity], ['Maximum usage-context similarity', v.aidebt_sii_maximum_context_similarity], ['Concept-similarity boundary', v.aidebt_sii_semantic_threshold], ['Usage-context boundary', v.aidebt_sii_context_threshold], ['Name-similarity ceiling', v.aidebt_sii_lexical_threshold]],
      EGR: [['Complexity boundary', v.aidebt_egr_cc_threshold], ['Nesting boundary', v.aidebt_egr_nesting_threshold]]
    }[short] || [];
    return rows.map(([label, value]) => ({label, value: number(value, 3)}));
  }

  function csdEvidenceSection(values) {
    const section = el('section', 'aidebt-finding-section aidebt-csd-section');
    let evidence;
    try { evidence = JSON.parse(values.aidebt_csd_evidence || '{}'); } catch (_) { evidence = {}; }
    const transitions = Array.isArray(evidence.switches) ? evidence.switches.slice()
      : Array.isArray(evidence.transitions) ? evidence.transitions.filter(transition => transition.switch) : [];
    const switchCount = isNumber(values.aidebt_csd_switches) ? Math.round(values.aidebt_csd_switches) : transitions.length;
    const transitionCount = isNumber(values.aidebt_csd_transitions) ? Math.round(values.aidebt_csd_transitions) : transitions.length;
    section.appendChild(sectionTitle('Detected CSD switches',
      `${switchCount} detected switch${switchCount === 1 ? '' : 'es'} from ${transitionCount} analyzed transition${transitionCount === 1 ? '' : 's'}`));
    const note = el('div', 'aidebt-analysis-note');
    note.append(el('strong', '', 'Interpretation'), el('span', 'aidebt-multiline',
      'CSD compares naming style, coding idioms, and structural shape between consecutive same-scope callables.\n' +
      'Similarity ranges from 0 (very different) to 1 (very similar); a value below the configured boundary is treated as a switch.'));
    section.appendChild(note);
    if (!transitions.length) {
      section.appendChild(el('p', 'aidebt-empty-findings',
        transitionCount ? 'No transition crossed the active context-switch threshold.'
          : 'No same-scope block transition existed. A scope needs at least two callable blocks to contribute to CSD.'));
      return section;
    }
    transitions.sort((left, right) => left.similarity - right.similarity);
    const table = el('div', 'aidebt-csd-table');
    const header = el('div', 'aidebt-csd-row aidebt-csd-header');
    header.append(el('span', '', 'Transition'), el('span', '', 'Location'), el('span', '', 'Similarity'),
      el('span', '', 'Naming'), el('span', '', 'Patterns'), el('span', '', 'Structure'), el('span', '', 'Switch rule'));
    table.appendChild(header);
    transitions.forEach(transition => {
      const from = transition.from || {};
      const to = transition.to || {};
      const components = transition.components || {};
      const row = el('div', 'aidebt-csd-row');
      row.append(el('strong', '', `${from.name || 'previous'} → ${to.name || 'next'}`),
        el('span', '', `${displayPath(transition.file)} · ${transition.scope || '<module>'} · L${integer(from.startLine)}→L${integer(to.startLine)}`),
        el('strong', riskClass(1 - Number(transition.similarity)), number(transition.similarity, 3)),
        el('span', '', number(components.namingStyle, 3)),
        el('span', '', number(components.codingPatterns, 3)),
        el('span', '', number(components.structuralShape, 3)),
        el('span', '', `similarity < ${number(transition.threshold, 3)}`));
      table.appendChild(row);
    });
    section.appendChild(table);
    return section;
  }

  function rlrEvidenceSection(values) {
    const section = el('section', 'aidebt-finding-section aidebt-rlr-section');
    let evidence;
    try { evidence = JSON.parse(values.aidebt_rlr_evidence || '{}'); } catch (_) { evidence = {}; }
    const pairs = Array.isArray(evidence.pairs) ? evidence.pairs : [];
    const total = isNumber(evidence.total) ? Math.round(evidence.total)
      : isNumber(values.aidebt_rlr_redundant) ? Math.round(values.aidebt_rlr_redundant) : pairs.length;
    section.appendChild(sectionTitle('Detected redundant callable pairs',
      `${total} pair${total === 1 ? '' : 's'} crossed at least one active similarity threshold`));
    const note = el('div', 'aidebt-analysis-note');
    note.append(el('strong', '', 'Interpretation'), el('span', '',
      'Each finding compares two complete callables. Syntax similarity measures overlap between normalized token sequences; behavioral similarity compares static structure, calls, and returned values. A match is a candidate for consolidation, not proof that the callables are equivalent at runtime.'));
    section.appendChild(note);
    if (evidence.budgetReached) {
      section.appendChild(el('p', 'aidebt-budget-warning',
        `Pair budget reached: ${integer(evidence.analyzedPairs)} of ${integer(evidence.candidatePairs)} possible callable pairs were analyzed. Increase sonar.aidebt.pairBudget and rescan before treating this as a complete project-wide ratio.`));
    }
    if (!pairs.length) {
      section.appendChild(el('p', 'aidebt-empty-findings',
        isNumber(values.aidebt_rlr_pairs) && values.aidebt_rlr_pairs > 0
          ? 'No callable pair crossed either active redundancy threshold.'
          : 'Fewer than two analyzable callable blocks were available.'));
      return section;
    }
    const list = el('div', 'aidebt-rlr-pairs');
    pairs.forEach((pair, index) => {
      const first = pair.first || {};
      const second = pair.second || {};
      const card = el('article', 'aidebt-rlr-pair aidebt-evidence-caution');
      const top = el('div', 'aidebt-finding-top');
      const matchLabels = {
        'syntax-and-semantic-proxy': 'BOTH SIGNALS',
        'semantic-proxy': 'BEHAVIOR MATCH',
        'syntax': 'SYNTAX MATCH'
      };
      top.append(pairHeading(`${first.name || 'first callable'} ↔ ${second.name || 'second callable'}`, index),
        el('span', 'aidebt-badge aidebt-status-caution', matchLabels[pair.matchedBy] || 'REDUNDANT PAIR'));
      const comparison = el('div', 'aidebt-rlr-comparison');
      comparison.append(rlrFact('Syntax similarity', number(pair.syntactic, 3)),
        rlrFact('Behavioral similarity', number(pair.semantic, 3)),
        rlrFact('Detected by', pair.matchedBy === 'syntax-and-semantic-proxy' ? 'both signals'
          : pair.matchedBy === 'semantic-proxy' ? 'behavioral similarity' : 'syntax similarity'));
      const sources = el('div', 'aidebt-rlr-sources');
      sources.append(rlrSource(first, 'First callable'), rlrSource(second, 'Second callable'));
      card.append(top, comparison, sources);
      list.appendChild(card);
    });
    section.appendChild(list);
    if (evidence.truncated) {
      section.appendChild(el('p', 'aidebt-artifact-limit',
        `Showing ${pairs.length} of ${total} detected pairs to keep the report responsive.`));
    }
    return section;
  }

  function rlrFact(label, value) {
    const fact = el('div', 'aidebt-rlr-fact');
    fact.append(el('span', '', label), el('strong', '', value));
    return fact;
  }

  function pairHeading(label, index) {
    const heading = el('strong', 'aidebt-pair-heading');
    heading.append(el('span', 'aidebt-pair-index', `PAIR ${index + 1}`), document.createTextNode(label));
    return heading;
  }

  function rlrSource(block, label) {
    const panel = el('div', 'aidebt-rlr-source');
    panel.append(el('span', 'aidebt-rlr-source-label', label),
      el('strong', '', block.name || 'unknown callable'),
      el('div', 'aidebt-cii-location',
        `${displayPath(block.file)} · lines ${integer(block.startLine)}–${integer(block.endLine)}`));
    const source = el('code', 'aidebt-source-code', 'Loading callable source…');
    panel.appendChild(source);
    loadArtifactSource(block.file, block.startLine, block.endLine, source);
    return panel;
  }

  function siiEvidenceSection(values) {
    const section = el('section', 'aidebt-finding-section aidebt-sii-section');
    let evidence;
    try { evidence = JSON.parse(values.aidebt_sii_evidence || '{}'); } catch (_) { evidence = {}; }
    const pairs = Array.isArray(evidence.pairs) ? evidence.pairs : [];
    const total = isNumber(evidence.total) ? Math.round(evidence.total)
      : isNumber(values.aidebt_sii_inconsistent) ? Math.round(values.aidebt_sii_inconsistent) : pairs.length;
    section.appendChild(sectionTitle('Detected naming–context inconsistencies',
      `${total} identifier pair${total === 1 ? '' : 's'} combined similar name concepts and behavior with different vocabulary`));
    const note = el('div', 'aidebt-analysis-note');
    note.append(el('strong', '', 'Interpretation'), el('span', '',
      'Each reported pair contains identifiers of the same kind whose names map to similar programming concepts and whose AST usage contexts are also similar, while their normalized names remain lexically different. The pair is a naming-review candidate; the analyzer does not decide which name is preferable.'));
    section.appendChild(note);
    if (evidence.budgetReached) {
      section.appendChild(el('p', 'aidebt-budget-warning',
        `Pair budget reached after ${integer(evidence.analyzedPairs)} eligible identifier pairs. Increase sonar.aidebt.pairBudget and rescan before treating SII as a complete project-wide ratio.`));
    }
    if (!pairs.length) {
      section.appendChild(el('p', 'aidebt-empty-findings',
        isNumber(values.aidebt_sii_pairs) && values.aidebt_sii_pairs > 0
          ? 'No same-kind identifier pair satisfied both active inconsistency conditions.'
          : 'Fewer than two comparable contextual identifiers were available.'));
      return section;
    }
    const list = el('div', 'aidebt-sii-pairs');
    pairs.forEach((pair, index) => {
      const first = pair.first || {};
      const second = pair.second || {};
      const kind = String(first.kind || second.kind || 'identifier');
      const card = el('article', 'aidebt-sii-pair aidebt-evidence-caution');
      const top = el('div', 'aidebt-finding-top');
      top.append(pairHeading(`${first.name || 'first identifier'} ↔ ${second.name || 'second identifier'}`, index),
        el('span', 'aidebt-badge aidebt-status-caution', kind.toUpperCase()));
      const comparison = el('div', 'aidebt-rlr-comparison');
      comparison.append(rlrFact('Concept similarity', number(pair.semantic, 3)),
        rlrFact('Usage-context similarity', number(pair.context, 3)),
        rlrFact('Name similarity', number(pair.lexical, 3)),
        rlrFact('Interpretation', 'same concept · different vocabulary'));
      const sources = el('div', 'aidebt-rlr-sources');
      sources.append(siiSource(first, 'First identifier'), siiSource(second, 'Second identifier'));
      card.append(top, comparison, sources);
      list.appendChild(card);
    });
    section.appendChild(list);
    if (evidence.truncated) {
      section.appendChild(el('p', 'aidebt-artifact-limit',
        `Showing ${pairs.length} of ${total} detected pairs to keep the report responsive.`));
    }
    return section;
  }

  function siiSource(identifier, label) {
    const panel = el('div', 'aidebt-rlr-source');
    panel.append(el('span', 'aidebt-rlr-source-label', label),
      el('strong', '', identifier.name || 'unknown identifier'),
      el('div', 'aidebt-cii-location',
        `${displayPath(identifier.file)} · ${identifier.scope || '<module>'} · lines ${integer(identifier.startLine)}–${integer(identifier.endLine)}`));
    const source = el('code', 'aidebt-source-code', 'Loading identifier context…');
    panel.appendChild(source);
    loadArtifactSource(identifier.file, identifier.startLine, identifier.endLine, source);
    return panel;
  }

  function egrEvidenceSection(values) {
    const section = el('section', 'aidebt-finding-section aidebt-egr-section');
    let evidence;
    try { evidence = JSON.parse(values.aidebt_egr_evidence || '{}'); } catch (_) { evidence = {}; }
    const blocks = Array.isArray(evidence.blocks) ? evidence.blocks : [];
    const total = isNumber(evidence.total) ? Math.round(evidence.total)
      : isNumber(values.aidebt_egr_complex) ? Math.round(values.aidebt_egr_complex) : blocks.length;
    const gaps = isNumber(evidence.gaps) ? Math.round(evidence.gaps)
      : isNumber(values.aidebt_egr_unexplained) ? Math.round(values.aidebt_egr_unexplained) : blocks.filter(block => !block.hasRationale).length;
    section.appendChild(sectionTitle('Complex-block explanation audit',
      `${gaps} explanation gap${gaps === 1 ? '' : 's'} across ${total} complex or critical block${total === 1 ? '' : 's'}`));
    const note = el('div', 'aidebt-analysis-note');
    note.append(el('strong', '', 'Interpretation'), el('span', '',
      'A callable enters this audit when it crosses the calibrated complexity, nesting, or combined control-flow criterion. It is considered explained only when an associated comment or docstring states a reason, constraint, invariant, safety concern, fallback, performance decision, or validation intent.'));
    section.appendChild(note);
    if (!blocks.length) {
      section.appendChild(el('p', 'aidebt-empty-findings',
        total ? 'Current EGR evidence is unavailable; run a fresh analysis with the installed plugin.'
          : 'No callable block crossed any active complex/critical selection criterion.'));
      return section;
    }

    const table = el('div', 'aidebt-egr-table');
    const header = el('div', 'aidebt-egr-row aidebt-egr-header');
    header.append(el('span', '', 'Block'), el('span', '', 'Location'), el('span', '', 'Complexity'),
      el('span', '', 'Nesting'), el('span', '', 'Selection criteria'),
      el('span', '', 'Explanation'));
    table.appendChild(header);
    blocks.forEach(block => {
      const row = el('div', 'aidebt-egr-row');
      row.append(el('strong', '', block.name || 'callable'),
        el('span', '', `${displayPath(block.file)} · L${integer(block.startLine)}–${integer(block.endLine)}`),
        el('span', '', integer(block.complexity)), el('span', '', integer(block.nesting)),
        el('span', '', egrLabels(block.triggers, 'criterion')),
        el('span', `aidebt-badge ${block.hasRationale ? 'aidebt-status-positive' : 'aidebt-status-caution'}`,
          block.hasRationale ? String(block.rationaleSource || 'rationale').toUpperCase() : 'MISSING'));
      table.appendChild(row);
    });

    const gapHeading = el('h3', 'aidebt-cii-subtitle', 'Source evidence for selected blocks');
    const list = el('div', 'aidebt-egr-gaps');
    blocks.forEach(block => {
        const card = el('article', `aidebt-egr-gap ${block.hasRationale ? 'aidebt-evidence-positive' : 'aidebt-evidence-caution'}`);
        const top = el('div', 'aidebt-finding-top');
        top.append(el('strong', '', block.name || 'callable'),
          el('span', `aidebt-badge ${block.hasRationale ? 'aidebt-status-positive' : 'aidebt-status-caution'}`,
            block.hasRationale ? `${String(block.rationaleSource || 'rationale').toUpperCase()} RATIONALE` : 'MISSING RATIONALE'));
        const location = el('div', 'aidebt-cii-location',
          `${displayPath(block.file)} · lines ${integer(block.startLine)}–${integer(block.endLine)}`);
        const source = el('code', 'aidebt-source-code', 'Loading complex block…');
        card.append(top, location, source);
        loadArtifactSource(block.file, block.startLine, block.endLine, source);
        list.appendChild(card);
    });
    section.append(table, gapHeading, list);
    if (evidence.truncated) {
      section.appendChild(el('p', 'aidebt-artifact-limit',
        `Showing ${blocks.length} of ${total} selected blocks to keep the report responsive.`));
    }
    return section;
  }

  function egrLabels(values, fallback) {
    if (!Array.isArray(values) || !values.length) return fallback;
    const labels = {
      'cyclomatic-complexity': 'cyclomatic complexity',
      'deep-nesting': 'deep nesting',
      'mixed-control-flow': 'multiple control-flow types',
      'branch': 'branching',
      'iteration': 'iteration',
      'error': 'error handling',
      'async': 'async flow',
      'resource': 'resource scope'
    };
    return values.map(value => labels[value] || value).join(' · ');
  }

  function ciiEvidenceSection(values) {
    const section = el('section', 'aidebt-finding-section aidebt-cii-section');
    section.appendChild(sectionTitle('CII dependency evidence', 'Per-file instability and source-located imports'));
    let evidence;
    try {
      evidence = JSON.parse(values.aidebt_cii_evidence || '{}');
    } catch (_) {
      evidence = {};
    }
    const files = Array.isArray(evidence.files) ? evidence.files : [];
    const dependencies = Array.isArray(evidence.dependencies) ? evidence.dependencies : [];
    const cycles = Array.isArray(evidence.cycles) ? evidence.cycles : [];
    const stabilityViolations = Array.isArray(evidence.stabilityViolations) ? evidence.stabilityViolations : [];
    if (!files.length && !dependencies.length) {
      section.appendChild(el('p', 'aidebt-empty-findings', 'No incoming or outgoing Python dependencies were detected.'));
      return section;
    }

    const actionHeading = el('h3', 'aidebt-cii-subtitle', 'Actionable coupling findings');
    const actions = el('div', 'aidebt-cii-actions');
    cycles.forEach(cycle => {
      const card = el('article', 'aidebt-cii-action aidebt-cii-cycle-action');
      card.append(el('strong', '', `Dependency cycle ${integer(cycle.id)}`),
        el('span', 'aidebt-badge aidebt-severity-high', '60 min'),
        el('p', '', (cycle.modules || []).map(displayPath).join(' → ')));
      actions.appendChild(card);
    });
    stabilityViolations.forEach(item => {
      const card = el('article', 'aidebt-cii-action aidebt-cii-stability-action');
      card.append(el('strong', '', 'Dependency toward a less stable module'),
        el('span', 'aidebt-badge aidebt-status-caution', '20 min'),
        el('p', '', `${displayPath(item.source)} (${number(item.sourceInstability, 3)}) → ${displayPath(item.target)} (${number(item.targetInstability, 3)}) · line ${integer(item.line)}`));
      actions.appendChild(card);
    });
    if (!cycles.length && !stabilityViolations.length) {
      actions.appendChild(el('p', 'aidebt-empty-findings', 'No internal dependency cycle or dependency toward a less stable analyzed module was detected.'));
    }

    const fileHeading = el('h3', 'aidebt-cii-subtitle', 'Per-file CII');
    const table = el('div', 'aidebt-cii-table');
    const header = el('div', 'aidebt-cii-row aidebt-cii-header');
    header.append(el('span', '', 'File'), el('span', '', 'Caᵢ'), el('span', '', 'Ceᵢ'), el('span', '', 'CIIᵢ'));
    table.appendChild(header);
    files.forEach(file => {
      const row = el('div', 'aidebt-cii-row');
      row.append(el('code', '', displayPath(file.file)), el('strong', '', integer(file.ca)),
        el('strong', '', integer(file.ce)), el('strong', riskClass(file.cii), number(file.cii, 3)));
      table.appendChild(row);
    });

    const dependencyHeading = el('h3', 'aidebt-cii-subtitle', 'Detected import dependencies');
    const list = el('div', 'aidebt-cii-dependencies');
    dependencies.forEach(dependency => {
      const card = el('article', `aidebt-cii-dependency aidebt-cii-${dependency.kind}`);
      const top = el('div', 'aidebt-cii-dependency-top');
      top.append(el('strong', '', dependency.module),
        el('span', `aidebt-badge ${dependency.kind === 'internal' ? 'aidebt-status-info' : 'aidebt-status-caution'}`, dependency.kind));
      const route = dependency.kind === 'internal'
        ? `${displayPath(dependency.source)} → ${displayPath(dependency.target)}`
        : `${displayPath(dependency.source)} → external module`;
      const location = el('div', 'aidebt-cii-location', `${route} · line ${integer(dependency.line)}`);
      const source = el('code', 'aidebt-source-code', 'Loading import line…');
      card.append(top, location, source);
      loadImportLine(dependency, source);
      list.appendChild(card);
    });
    section.append(actionHeading, actions, fileHeading, table, dependencyHeading, list);
    return section;
  }

  function displayPath(path) {
    if (!path) return 'unknown';
    const normalized = String(path).replaceAll('\\\\', '/');
    const sourceIndex = normalized.lastIndexOf('/src/');
    return sourceIndex >= 0 ? normalized.slice(sourceIndex + 1) : normalized.split('/').slice(-2).join('/');
  }

  function loadImportLine(dependency, target) {
    const relative = displayPath(dependency.source);
    const key = `${options.component.key}:${relative}`;
    const from = Math.max(1, Number(dependency.line) || 1);
    fetch(`${window.baseUrl || ''}/api/sources/lines?key=${encodeURIComponent(key)}&from=${from}&to=${from}`)
      .then(response => response.ok ? response.json() : {sources: []})
      .then(payload => renderSourceLines(target, payload.sources || []))
      .catch(() => { target.textContent = `import ${dependency.module}`; });
  }

  function cdiEvidenceSection(values) {
    const section = el('section', 'aidebt-finding-section aidebt-cdi-section');
    section.appendChild(sectionTitle('CDI block evidence', 'Complexity and documentation status for every analyzed function'));
    let evidence;
    try {
      evidence = JSON.parse(values.aidebt_cdi_evidence || '{}');
    } catch (_) {
      evidence = {};
    }
    const blocks = Array.isArray(evidence.blocks) ? evidence.blocks : [];
    if (!blocks.length) {
      section.appendChild(el('p', 'aidebt-empty-findings', 'No analyzable Python function was found.'));
      return section;
    }

    const note = el('div', 'aidebt-cdi-note');
    note.append(el('strong', '', 'Interpretation'), el('span', '',
      'Callable blocks and decision points are different units: each callable can contribute zero, one, or several decision points. “Documented blocks” counts callables with an associated comment or docstring, while the decision-point totals measure the complexity carried by those callables.'));
    const table = el('div', 'aidebt-cdi-table');
    const header = el('div', 'aidebt-cdi-row aidebt-cdi-header');
    header.append(el('span', '', 'Callable block'), el('span', '', 'Lines'), el('span', '', 'Complexity'),
      el('span', '', 'Comment lines'), el('span', '', 'Documentation'));
    table.appendChild(header);
    blocks.forEach(block => {
      const row = el('div', 'aidebt-cdi-row');
      const documentation = block.documented ? 'documented' : 'undocumented';
      row.append(el('code', '', `${displayPath(block.file)} · ${block.name}`),
        el('span', '', `${integer(block.startLine)}–${integer(block.endLine)}`),
        el('strong', riskClass(Math.min(1, Number(block.complexity) / 10)), integer(block.complexity)),
        el('strong', '', integer(block.commentLines)),
        el('span', `aidebt-badge ${block.documented ? 'aidebt-status-positive' : 'aidebt-status-caution'}`, documentation));
      table.appendChild(row);
    });

    const detailHeading = el('h3', 'aidebt-cii-subtitle', 'Source blocks');
    const list = el('div', 'aidebt-cdi-blocks');
    blocks.forEach(block => {
      const card = el('article', `aidebt-cdi-block ${block.documented ? 'aidebt-cdi-documented' : 'aidebt-cdi-undocumented'}`);
      const top = el('div', 'aidebt-cii-dependency-top');
      top.append(el('strong', '', block.name),
        el('span', `aidebt-badge ${block.documented ? 'aidebt-status-positive' : 'aidebt-status-caution'}`,
          block.documented ? 'documented' : 'undocumented'));
      const facts = el('div', 'aidebt-cii-location',
        `${displayPath(block.file)} · lines ${integer(block.startLine)}–${integer(block.endLine)} · complexity ${integer(block.complexity)} · nesting ${integer(block.nesting)}`);
      const source = el('code', 'aidebt-source-code', 'Loading function source…');
      card.append(top, facts, source);
      loadArtifactSource(block.file, block.startLine, block.endLine, source);
      list.appendChild(card);
    });
    section.append(note, table, detailHeading, list);
    return section;
  }

  function loadArtifactSource(file, startLine, endLine, target) {
    const relative = displayPath(file);
    const key = `${options.component.key}:${relative}`;
    const from = Math.max(1, Number(startLine) || 1);
    const to = Math.max(from, Number(endLine) || from);
    const cacheKey = `${key}:${from}:${to}`;
    const request = sourceCache.get(cacheKey) || fetch(`${window.baseUrl || ''}/api/sources/lines?key=${encodeURIComponent(key)}&from=${from}&to=${to}`)
      .then(response => response.ok ? response.json() : {sources: []})
      .then(payload => payload.sources || [])
      .catch(() => []);
    sourceCache.set(cacheKey, request);
    request.then(lines => renderSourceLines(target, lines));
  }

  function htsEvidenceSection(values) {
    const section = el('section', 'aidebt-finding-section aidebt-hts-section');
    section.appendChild(sectionTitle('HTS initialization evidence', 'Every ML initialization classified in the current analysis'));
    const note = el('div', 'aidebt-analysis-note');
    note.append(el('strong', '', 'Interpretation'), el('span', '',
      'HTS classifies each supported model initialization by how clearly its configuration is expressed in source code. Explicit and statically resolvable configuration is transparent; missing or opaque configuration requires review.'));
    section.appendChild(note);
    let evidence;
    try { evidence = JSON.parse(values.aidebt_hts_evidence || '{}'); } catch (_) { evidence = {}; }
    const initializations = Array.isArray(evidence.initializations) ? evidence.initializations : [];
    if (!initializations.length) {
      section.appendChild(el('p', 'aidebt-empty-findings',
        isNumber(values.aidebt_hts) ? 'No initialization evidence was published; run a fresh scan with the current plugin.' : 'No supported ML initialization was detected.'));
      return section;
    }
    const list = el('div', 'aidebt-finding-list');
    initializations.forEach(item => {
      const card = el('article', `aidebt-finding-card ${['explicit','config-driven'].includes(item.category) ? 'aidebt-evidence-positive' : 'aidebt-evidence-caution'}`);
      const top = el('div', 'aidebt-finding-top');
      const categoryClass = ['explicit','config-driven'].includes(item.category) ? 'aidebt-status-positive' : 'aidebt-status-caution';
      top.append(el('strong', '', item.constructor || 'ML initialization'),
        el('span', `aidebt-badge ${categoryClass}`, String(item.category || 'unknown').toUpperCase()));
      const location = el('div', 'aidebt-cii-location', `${displayPath(item.file)} · line ${integer(item.line)}`);
      const source = el('code', 'aidebt-source-code', 'Loading initialization source…');
      const details = el('div', 'aidebt-finding-explanation');
      const configuration = item.category === 'implicit' ? 'No constructor arguments or **configuration were supplied.'
        : item.category === 'opaque' ? `Expanded configuration ${listText(item.configExpansions)} could not be resolved to static keys.`
        : item.category === 'config-driven' ? `Resolved configuration keys: ${listText(item.resolvedConfigKeys)}.`
        : `Explicit keyword arguments: ${listText(item.keywordArguments)}${Number(item.positionalArguments) ? `; positional arguments: ${integer(item.positionalArguments)}` : ''}.`;
      details.append(htsDetail('Classification', configuration),
        htsDetail('Framework', item.framework || 'unknown'));
      card.append(top, location, source, details);
      loadArtifactSource(item.file, item.line, item.line, source);
      list.appendChild(card);
    });
    section.appendChild(list);
    return section;
  }

  function htsDetail(label, value) {
    const row = el('div', 'aidebt-finding-copy');
    row.append(el('strong', '', label), el('span', '', value));
    return row;
  }

  function listText(values) {
    return Array.isArray(values) && values.length ? values.join(', ') : 'none';
  }

  function rulesForMetric(short) {
    if (short === 'HTS') return ['opaque-ml-config','missing-random-seed','unpinned-model-revision'];
    if (short === 'CII') return ['coupling-cycle','unstable-dependency-direction'];
    if (short === 'CSD') return ['context-switch'];
    if (short === 'RLR') return ['redundant-logic'];
    if (short === 'SII') return ['semantic-name-inconsistency'];
    if (short === 'EGR') return ['explanation-gap'];
    if (short === 'AISD') return specDetectRules;
    return [];
  }

  function findingList(short, issues) {
    const section = el('section', 'aidebt-finding-section');
    section.appendChild(sectionTitle(`${short} detected evidence`, issues.length ? `${issues.length} source-located candidate${issues.length === 1 ? '' : 's'}` : 'No candidate crossed the active threshold'));
    if (!issues.length) {
      section.appendChild(el('p', 'aidebt-empty-findings', 'A zero score is evidence-backed when analyzed pairs exist but none crosses the configured threshold.'));
      return section;
    }
    const list = el('div', 'aidebt-finding-list');
    issues.forEach(issue => {
      const card = el('article', `aidebt-finding-card ${severityClass(issue.severity)}`);
      const top = el('div', 'aidebt-finding-top');
      const rule = ruleForIssue(issue);
      const id = ((issue.message || '').match(/(?:AIDEBT-PY-\d{3}|SPECDETECT4AI-R(?:11bis|\d+))/i) || ['AI Debt'])[0];
      top.append(el('strong', '', `${id} · ${displayRuleTitle(id, rule)}`), el('span', `aidebt-badge ${severityClass(issue.severity)}`, issue.severity || 'INFO'));
      const location = el('div', 'aidebt-cii-location', issueLocation(issue));
      const source = el('code', 'aidebt-source-code', 'Loading source line…');
      card.append(top, location, source, findingExplanation(issue.message || 'Evidence: Source-located AI Debt finding'));
      loadSourceLine(issue, source);
      list.appendChild(card);
    });
    section.appendChild(list);
    return section;
  }

  function severityClass(severity) {
    if (severity === 'BLOCKER') return 'aidebt-severity-blocker';
    if (severity === 'CRITICAL' || severity === 'HIGH') return 'aidebt-severity-high';
    if (severity === 'MAJOR' || severity === 'MEDIUM') return 'aidebt-severity-medium';
    if (severity === 'MINOR' || severity === 'LOW') return 'aidebt-severity-low';
    return 'aidebt-severity-info';
  }

  function ruleForIssue(issue) {
    const id = ((issue.message || '').match(/(?:AIDEBT-PY-\d{3}|SPECDETECT4AI-R(?:11bis|\d+))/i) || [])[0];
    if (/^SPECDETECT4AI-/i.test(id)) return `specdetect-${id.split('-').pop().toLowerCase()}`;
    return ruleIds[id] || (issue.rule || '').split(':').pop() || 'ai-debt-finding';
  }

  function displayRuleTitle(id, rule) {
    const specId = /^SPECDETECT4AI-(R(?:11bis|\d+))$/i.exec(id);
    if (specId) return specDetectTitles[specId[1].toLowerCase()] || rule.replaceAll('-', ' ');
    return rule.replaceAll('-', ' ');
  }

  function findingExplanation(message) {
    const cleaned = message.replace(/^(?:AIDEBT-PY-\d{3}|SPECDETECT4AI-R(?:11bis|\d+))\s*·?\s*/i, '');
    const match = cleaned.match(/^Evidence:\s*(.*?)\s+Rationale:\s*(.*?)\s+Recommendation:\s*(.*)$/);
    const values = match ? {Evidence: match[1], Rationale: match[2], Recommendation: match[3]} : {Evidence: cleaned};
    const box = el('div', 'aidebt-finding-explanation');
    Object.entries(values).forEach(([label, value]) => {
      const row = el('div', 'aidebt-finding-copy');
      row.append(el('strong', '', label), el('span', '', value));
      box.appendChild(row);
    });
    return box;
  }

  function loadSourceLine(issue, target) {
    const startLine = issueStartLine(issue);
    if (!issue.component || !startLine) { target.textContent = 'Source line unavailable'; return; }
    const endLine = issue.textRange && issue.textRange.endLine ? issue.textRange.endLine : startLine;
    const key = `${issue.component}:${startLine}:${endLine}`;
    const request = sourceCache.get(key) || fetch(`${window.baseUrl || ''}/api/sources/lines?key=${encodeURIComponent(issue.component)}&from=${startLine}&to=${endLine}`)
      .then(response => response.ok ? response.json() : {sources: []})
      .then(payload => payload.sources || [])
      .catch(() => []);
    sourceCache.set(key, request);
    request.then(lines => renderSourceLines(target, lines));
  }

  function issueStartLine(issue) {
    return Number(issue.line || (issue.textRange && issue.textRange.startLine)) || 0;
  }

  function issueLocation(issue) {
    const component = String(issue.component || 'unknown');
    const separator = component.indexOf(':');
    const file = displayPath(separator >= 0 ? component.slice(separator + 1) : component);
    const line = issueStartLine(issue);
    return line ? `${file} · line ${line}` : file;
  }

  function renderSourceLines(target, lines) {
    target.innerHTML = '';
    if (!lines.length) { target.textContent = 'Source block unavailable'; return; }
    lines.forEach(item => {
      const row = el('span', 'aidebt-code-line');
      row.append(el('span', 'aidebt-line-number', String(item.line)), el('span', 'aidebt-line-content', plainSource(item.code || '')));
      target.appendChild(row);
    });
  }

  function plainSource(code) {
    const holder = document.createElement('div');
    holder.innerHTML = code;
    return holder.textContent || '';
  }

  function evidenceGrid(items) {
    const grid = el('div', 'aidebt-evidence-grid');
    items.forEach(item => {
      const cell = el('div', 'aidebt-evidence');
      cell.append(el('span', '', item.label), el('strong', '', item.value));
      grid.appendChild(cell);
    });
    return grid;
  }

  function metricDefinitions(v) {
    const cdiInputsReady = isNumber(v.aidebt_cdi_total_complexity_excess)
      && isNumber(v.aidebt_cdi_undocumented_complexity_excess);
    return [
      metric('AISD', 'AI-associated smell density', v.aidebt_aisd_score, v.aidebt_weight_aisd,
        [{label:'Smells',value:integer(v.aidebt_aisd_smells)},{label:'KLOC',value:number(v.aidebt_aisd_kloc,3)},{label:'Raw density',value:number(v.aidebt_aisd,3)}],
        'Shows how strongly the analyzed code is affected by AI-associated implementation patterns detected by the configured rule set.',
        'It relates detected SpecDetect4AI findings to the amount of analyzed source code while preserving each finding as source-located evidence.',
        'Higher values indicate a denser concentration of AI-associated risks that deserve closer review.', 'No logical source lines were available.'),
      metric('CII', 'Coupling instability index', v.aidebt_cii, v.aidebt_weight_cii,
        [{label:'Project total Ca',value:integer(v.aidebt_cii_ca)},{label:'Project total Ce',value:integer(v.aidebt_cii_ce)},{label:'Project internal import edges',value:integer(v.aidebt_cii_internal_dependencies)},{label:'Project external import edges',value:integer(v.aidebt_cii_external_dependencies)},{label:'Coupled files in mean',value:integer(v.aidebt_cii_coupled_files)},{label:'Dependency cycles',value:integer(v.aidebt_cii_cycles)},{label:'Stability-direction violations',value:integer(v.aidebt_cii_stability_violations)},{label:'Remediation actions',value:integer(v.aidebt_cii_remediation_actions)}],
        'Describes how dependent project modules are on other modules and how exposed they are to change.',
        'It examines incoming and outgoing dependencies for each coupled Python file and summarizes project-level structural instability.',
        'Higher values indicate modules that rely more heavily on outward dependencies and may be more fragile under change.', 'No incoming or outgoing dependencies were detected.'),
      metric('CDI', 'Complexity–documentation imbalance', cdiInputsReady ? v.aidebt_cdi_score : undefined, v.aidebt_weight_cdi,
        [{label:'Analyzed callable blocks',value:integer(v.aidebt_cdi_blocks)},{label:'Mean cyclomatic complexity',value:number(v.aidebt_cdi_mean_complexity,3)},{label:'Total decision points',value:cdiInputsReady ? integer(v.aidebt_cdi_total_complexity_excess) : 'N/A'},{label:'Undocumented decision points',value:cdiInputsReady ? integer(v.aidebt_cdi_undocumented_complexity_excess) : 'N/A'},{label:'Documented blocks',value:integer(v.aidebt_cdi_documented_blocks)},{label:'Documentation coverage',value:percent(v.aidebt_cdi_documentation_coverage)},{label:'Comment density',value:percent(v.aidebt_cdi_comment_density)},{label:'Comment lines',value:integer(v.aidebt_cdi_comment_lines)},{label:'Source lines',value:integer(v.aidebt_cdi_source_lines)}],
        cdiInputsReady ? 'Highlights complex callable blocks whose intent and behavior are not adequately supported by nearby documentation.' : 'A fresh analysis is required for the current CDI evidence model.',
        'It compares callable-level cyclomatic complexity with comments, docstrings, and documentation coverage across the project.',
        'Higher values indicate that a larger share of decision complexity is insufficiently documented.', 'No analyzable block was found.'),
      metric('HTS', 'Hyperparameter transparency score', isNumber(v.aidebt_hts) ? 1 - v.aidebt_hts : undefined, v.aidebt_weight_hts,
        [{label:'Implicit initializations',value:integer(v.aidebt_hts_implicit)},{label:'Opaque **kwargs initializations',value:integer(v.aidebt_hts_opaque_config)},{label:'All ML initializations',value:integer(v.aidebt_hts_total)},{label:'Explicit arguments',value:integer(v.aidebt_hts_explicit)},{label:'Resolvable **kwargs configuration',value:integer(v.aidebt_hts_config_driven)},{label:'Missing random seed',value:integer(v.aidebt_hts_missing_seed)},{label:'Unpinned model revision',value:integer(v.aidebt_hts_unpinned_revision)}],
        'Assesses whether machine-learning model configuration is visible and reproducible from the analyzed source code.',
        'It classifies supported model initializations as explicit, resolvable configuration-driven, implicit, or opaque, with additional reproducibility diagnostics.',
        'Unlike debt metrics, a higher HTS is better: it means model configuration is more transparent and reproducible.', 'No supported ML model initialization was detected.', v.aidebt_hts, true),
      metric('CSD', 'Context switching density', v.aidebt_csd, v.aidebt_weight_csd,
        [{label:'Low-similarity switches',value:integer(v.aidebt_csd_switches)},{label:'Block transitions',value:integer(v.aidebt_csd_transitions)}],
        'Estimates how often consecutive callable blocks abruptly break the coding style and structural pattern a reader has just formed.',
        'It compares naming conventions, coding idioms, and structural shape between neighboring same-scope callables.',
        'Higher values indicate more frequent style–structure discontinuities that may force readers to rebuild their mental model.', 'Fewer than two same-scope adjacent callable blocks existed.'),
      metric('RLR', 'Redundant logic ratio', v.aidebt_rlr, v.aidebt_weight_rlr,
        [{label:'Redundant pairs',value:integer(v.aidebt_rlr_redundant)},{label:'Analyzed pairs',value:integer(v.aidebt_rlr_pairs)}],
        'Estimates how much implementation logic is repeated across callable blocks in the project.',
        'It evaluates callable pairs for strong syntactic overlap or closely aligned structural behavior.',
        'Higher values suggest duplicated behavior that may require repeated maintenance and can drift over time.', 'Fewer than two analyzable blocks were available.'),
      metric('SII', 'Semantic inconsistency index', v.aidebt_sii, v.aidebt_weight_sii,
        [{label:'Inconsistent pairs',value:integer(v.aidebt_sii_inconsistent)},{label:'Identifier pairs',value:integer(v.aidebt_sii_pairs)},{label:'Contextual identifiers',value:integer(v.aidebt_sii_identifiers)}],
        'Detects vocabulary drift: identifiers of the same kind that appear to represent the same concept in similar code contexts but use noticeably different names.',
        'It normalizes identifier tokens into programming concepts, compares how the identifiers are used in the AST, and measures lexical name similarity.',
        'Higher values suggest that readers must learn multiple names for the same project concept, increasing interpretation and maintenance effort.', 'Fewer than two same-kind identifiers with analyzable usage context were available.'),
      metric('EGR', 'Explanation gap ratio', v.aidebt_egr, v.aidebt_weight_egr,
        [{label:'Without rationale',value:integer(v.aidebt_egr_unexplained)},{label:'Complex / critical blocks',value:integer(v.aidebt_egr_complex)},{label:'Cyclomatic-complexity-triggered',value:integer(v.aidebt_egr_cc_triggered)},{label:'Nesting-triggered',value:integer(v.aidebt_egr_nesting_triggered)},{label:'Mixed-flow-triggered',value:integer(v.aidebt_egr_control_flow_triggered)}],
        'Identifies complex callable blocks that lack a meaningful explanation of why the complexity is necessary.',
        'It selects blocks using cyclomatic complexity, deep nesting, and mixed control-flow signals, then checks associated comments or docstrings for rationale language.',
        'Higher values mean more complex behavior is left without an explanatory rationale.', 'No block met the configured complexity threshold.')
    ];
  }

  function metric(short, name, score, weight, evidence, description, measures, interpretation, naReason, displayScore, benefit) {
    return {short, name, score, displayScore: isNumber(displayScore) ? displayScore : score, benefit: Boolean(benefit),
      weight: isNumber(weight) ? weight : 0.25, evidence, description, measures, interpretation, naReason};
  }

  function calculateIndex(name, metrics, published) {
    const availableWeight = metrics.filter(metric => isNumber(metric.score)).reduce((sum, metric) => sum + metric.weight, 0);
    const terms = metrics.map(metric => {
      const applicable = isNumber(metric.score) && availableWeight > 0;
      const effectiveWeight = applicable ? metric.weight / availableWeight : 0;
      return {...metric, applicable, configuredWeight: metric.weight, effectiveWeight,
        contribution: applicable ? effectiveWeight * metric.score : 0};
    });
    const result = terms.reduce((sum, term) => sum + term.contribution, 0);
    const expression = availableWeight === 0 ? `${name}: no applicable components` : `${name}: published result ${fixed(result)}`;
    return {name, terms, result: isNumber(published) ? published : result, available: availableWeight > 0, expression};
  }

  function calculateFinal(v, td, cog) {
    const tdWeight = isNumber(v.aidebt_weight_tdsi) ? v.aidebt_weight_tdsi : 0.5;
    const cogWeight = isNumber(v.aidebt_weight_cogdi) ? v.aidebt_weight_cogdi : 0.5;
    const available = (td.available ? tdWeight : 0) + (cog.available ? cogWeight : 0);
    const tdEffective = td.available && available ? tdWeight / available : 0;
    const cogEffective = cog.available && available ? cogWeight / available : 0;
    const result = isNumber(v.aidebt_adsi) ? v.aidebt_adsi : tdEffective * td.result + cogEffective * cog.result;
    const terms = [
      {short: 'TDSI', applicable: td.available, configuredWeight: tdWeight, effectiveWeight: tdEffective,
        contribution: td.available ? tdEffective * td.result : 0, naReason: 'No applicable technical metrics were available.'},
      {short: 'CogDI', applicable: cog.available, configuredWeight: cogWeight, effectiveWeight: cogEffective,
        contribution: cog.available ? cogEffective * cog.result : 0, naReason: 'No applicable cognitive metrics were available.'}
    ];
    return {result, terms,
      note: available < 1 ? 'At least one higher-order index was unavailable, so its configured weight was redistributed.'
        : 'Both technical and cognitive profiles contributed using their configured shares.'};
  }

  function effortLabel(estimate, metricLabel) {
    const item = el('div', 'aidebt-effort');
    if (!estimate.estimable) {
      item.title = estimate.reason || 'No defensible remediation unit is available.';
      item.append(el('span', '', 'Remediation workload'), el('strong', '', 'Not estimated'));
      return item;
    }
    if (metricLabel === 'CII' && (estimate.actions || 0) === 0) {
      item.title = 'CII was calculated, but no dependency cycle or stability-direction violation was detected.';
      item.append(el('span', '', 'Remediation effort'), el('strong', '', 'No action'));
      return item;
    }
    item.title = `${estimate.actions || 0} deduplicated remediation action(s).`;
    item.append(el('span', '', 'Remediation effort'),
      el('strong', '', duration(isNumber(estimate.minutes) ? estimate.minutes : estimate.centralMinutes)));
    return item;
  }

  function parseJson(value) {
    if (!value || typeof value !== 'string') return {};
    try { return JSON.parse(value); } catch (_) { return {}; }
  }

  function duration(minutes) {
    const total = Math.max(0, Math.round(minutes));
    if (total < 60) return `${total} min`;
    if (total < 480) {
      const hours = Math.floor(total / 60);
      const remainder = total % 60;
      return `${hours}h${remainder ? ` ${remainder}m` : ''}`;
    }
    const days = Math.floor(total / 480);
    const remainder = total % 480;
    const hours = Math.floor(remainder / 60);
    const mins = remainder % 60;
    return `${days}d${hours ? ` ${hours}h` : ''}${mins ? ` ${mins}m` : ''}`;
  }

  function effortConvention() {
    const note = el('aside', 'aidebt-effort-convention');
    note.append(el('span', 'aidebt-effort-convention-label', 'EFFORT STANDARD'),
      el('span', '', '1 day = 8 work hours'),
      el('span', 'aidebt-effort-convention-separator', '•'),
      el('span', '', 'SonarSource standard categories for Python'));
    return note;
  }

  function sectionTitle(title, subtitle) {
    const heading = el('div', 'aidebt-section-title');
    heading.append(el('h2', '', title), el('span', '', subtitle));
    return heading;
  }

  function scopeItem(value, label) {
    const item = el('span', '');
    item.append(el('strong', '', value), document.createTextNode(` ${label}`));
    return item;
  }

  function riskBadge(value) {
    const text = !isNumber(value) ? 'N/A' : value < 0.33 ? 'LOW' : value < 0.67 ? 'MODERATE' : 'HIGH';
    return el('span', `aidebt-badge ${riskClass(value)}`, text);
  }

  function benefitBadge(value) {
    const text = !isNumber(value) ? 'N/A' : value < 0.33 ? 'LOW' : value < 0.67 ? 'MODERATE' : 'HIGH';
    return el('span', `aidebt-badge ${riskClass(isNumber(value) ? 1 - value : value)}`, text);
  }

  function riskClass(value) {
    if (!isNumber(value)) return 'aidebt-na';
    if (value < 0.33) return 'aidebt-low';
    if (value < 0.67) return 'aidebt-medium';
    return 'aidebt-high';
  }

  function riskColor(value) {
    if (!isNumber(value)) return 'var(--ad-muted)';
    if (value < 0.33) return 'var(--ad-low)';
    if (value < 0.67) return 'var(--ad-medium)';
    return 'var(--ad-high)';
  }

  function fixed(value) { return isNumber(value) ? value.toFixed(3) : 'N/A'; }
  function number(value, digits) { return isNumber(value) ? value.toFixed(digits) : 'N/A'; }
  function integer(value) { return isNumber(value) ? Math.round(value).toLocaleString() : '0'; }
  function percent(value) { return isNumber(value) ? `${(value * 100).toFixed(1)}%` : 'N/A'; }
  function isNumber(value) { return Number.isFinite(value); }

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function showError(message) {
    root.innerHTML = '';
    root.appendChild(el('div', 'aidebt-error', `AI Debt dashboard could not load: ${message}`));
  }

  return function cleanup() {
    window.removeEventListener('resize', fitRootToViewport);
    root.style.removeProperty('width');
    root.style.removeProperty('max-width');
    root.classList.remove('aidebt-scroll-root');
    root.innerHTML = '';
  };
}, true);
