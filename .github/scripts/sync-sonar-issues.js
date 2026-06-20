const MANAGED_MARKER = 'managed-by:sonarqube-backlog-sync';
const GROUP_MARKER_PREFIX = 'sonar-group:';
const LABELS = [
    { name: 'sonarqube', color: '0E8A16', description: 'Managed SonarQube findings backlog' },
    { name: 'technical-debt', color: 'D4C5F9', description: 'Code quality improvements and remediation work' }
];
const MAX_FINDINGS_PER_ISSUE = 20;
const MAX_PAGE_SIZE = 500;

async function ensureLabels(github, owner, repo) {
    for (const label of LABELS) {
        try {
            await github.rest.issues.getLabel({
                owner,
                repo,
                name: label.name
            });
        } catch (error) {
            if (error.status !== 404) {
                throw error;
            }

            await github.rest.issues.createLabel({
                owner,
                repo,
                name: label.name,
                color: label.color,
                description: label.description
            });
        }
    }
}

async function fetchSonarIssues(baseUrl, sonarToken, projectKey) {
    const issues = [];
    let page = 1;
    const authHeader = 'Basic ' + Buffer.from(sonarToken + ':').toString('base64');

    while (true) {
        const url = new URL('/api/issues/search', baseUrl);
        url.searchParams.set('componentKeys', projectKey);
        url.searchParams.set('statuses', 'OPEN,REOPENED');
        url.searchParams.set('ps', String(MAX_PAGE_SIZE));
        url.searchParams.set('p', String(page));

        const response = await fetch(url, {
            headers: {
                Authorization: authHeader,
                Accept: 'application/json'
            }
        });

        if (!response.ok) {
            throw new Error(`SonarQube API request failed (${response.status} ${response.statusText})`);
        }

        const payload = await response.json();
        if (!Array.isArray(payload.issues)) {
            throw new Error('SonarQube API response did not include an issues array.');
        }

        if (payload.issues.length === 0) {
            break;
        }

        issues.push(...payload.issues);

        const responseTotal = payload.paging?.total ?? payload.total;
        if (typeof responseTotal !== 'number' || issues.length > responseTotal) {
            break;
        }

        page += 1;
    }

    return issues;
}

function groupIssues(issues) {
    const groups = new Map();

    for (const issue of issues) {
        const groupKey = `${issue.type}:${issue.rule}`;
        const group = groups.get(groupKey) ?? {
            key: groupKey,
            rule: issue.rule,
            type: issue.type,
            severity: issue.severity,
            issues: []
        };
        group.issues.push(issue);
        groups.set(groupKey, group);
    }

    return groups;
}

function escapeForHtmlComment(value) {
    return value.replace(/"/g, '&quot;');
}

function issueTitle(group) {
    return `SonarQube ${group.type}: ${group.rule} (${formatFindingCount(group.issues.length)})`;
}

function formatFindingCount(count) {
    return `${count} open ${formatFindingWord(count)}`;
}

function formatFindingWord(count) {
    return `finding${count === 1 ? '' : 's'}`;
}

function issueBody(baseUrl, projectKey, group) {
    const sortedIssues = [...group.issues].sort((left, right) => {
        const componentCompare = left.component.localeCompare(right.component);
        if (componentCompare !== 0) {
            return componentCompare;
        }

        return (left.line ?? 0) - (right.line ?? 0);
    });
    const firstIssue = sortedIssues[0];
    if (!firstIssue) {
        throw new Error(`Cannot build a backlog issue for empty SonarQube group ${group.key}.`);
    }
    const visibleIssues = sortedIssues.slice(0, MAX_FINDINGS_PER_ISSUE);
    const hiddenCount = sortedIssues.length - visibleIssues.length;

    const findings = visibleIssues
        .map((issue) => {
            const location = issue.line ? `${issue.component}:${issue.line}` : issue.component;
            const issueUrl = new URL('/project/issues', baseUrl);
            issueUrl.searchParams.set('id', projectKey);
            issueUrl.searchParams.set('issues', issue.key);

            return [
                `- \`${location}\``,
                `  - ${issue.message}`,
                `  - [Open in SonarQube](${issueUrl.toString()})`
            ].join('\n');
        })
        .join('\n');

    const omittedSummary = hiddenCount > 0
        ? `\n\n_Only the first ${MAX_FINDINGS_PER_ISSUE} findings are shown here. ${hiddenCount} additional ${formatFindingWord(hiddenCount)} remain open in SonarQube._`
        : '';

    return [
        `<!-- ${MANAGED_MARKER} -->`,
        `<!-- ${GROUP_MARKER_PREFIX}${escapeForHtmlComment(group.key)} -->`,
        `# ${group.rule}`,
        '',
        `- **Type:** ${group.type}`,
        `- **Severity:** ${group.severity}`,
        `- **Open findings:** ${formatFindingCount(sortedIssues.length)}`,
        `- **Project:** \`${projectKey}\``,
        `- **SonarQube view:** ${new URL(`/project/issues?id=${projectKey}&open=${firstIssue.key}`, baseUrl).toString()}`,
        '',
        'These findings were grouped automatically so GitHub backlog work can address repeated SonarQube problems together.',
        '',
        '## Findings',
        findings + omittedSummary
    ].join('\n');
}

function extractGroupKey(body = '') {
    const match = body.match(new RegExp(`<!-- ${GROUP_MARKER_PREFIX}([^>]+) -->`));
    return match?.[1];
}

function isIssue(issue) {
    return issue.pull_request === undefined;
}

module.exports = async function syncSonarIssues({ core, github, context }) {
    const owner = context.repo.owner;
    const repo = context.repo.repo;
    const sonarToken = process.env.SONAR_TOKEN;
    const sonarHostUrl = process.env.SONAR_HOST_URL;
    const sonarProjectKey = process.env.SONAR_PROJECT_KEY;

    if (!sonarToken || !sonarHostUrl || !sonarProjectKey) {
        core.setFailed('SONAR_TOKEN, SONAR_HOST_URL, and SONAR_PROJECT_KEY are required to sync SonarQube backlog issues.');
        return;
    }

    await ensureLabels(github, owner, repo);

    const sonarIssues = await fetchSonarIssues(sonarHostUrl, sonarToken, sonarProjectKey);
    const groupedIssues = groupIssues(sonarIssues);

    const existingIssues = await github.paginate(github.rest.issues.listForRepo, {
        owner,
        repo,
        state: 'all',
        labels: LABELS.map((label) => label.name).join(','),
        per_page: 100
    });

    const managedIssues = new Map(
        existingIssues
            .filter((issue) => isIssue(issue) && issue.body?.includes(MANAGED_MARKER))
            .map((issue) => [extractGroupKey(issue.body), issue])
            .filter(([groupKey]) => groupKey)
    );

    for (const group of groupedIssues.values()) {
        const title = issueTitle(group);
        const body = issueBody(sonarHostUrl, sonarProjectKey, group);
        const existingIssue = managedIssues.get(group.key);

        if (existingIssue) {
            await github.rest.issues.update({
                owner,
                repo,
                issue_number: existingIssue.number,
                title,
                body,
                labels: LABELS.map((label) => label.name),
                state: 'open'
            });
            managedIssues.delete(group.key);
            continue;
        }

        await github.rest.issues.create({
            owner,
            repo,
            title,
            body,
            labels: LABELS.map((label) => label.name)
        });
    }

    for (const staleIssue of managedIssues.values()) {
        await github.rest.issues.update({
            owner,
            repo,
            issue_number: staleIssue.number,
            state: 'closed'
        });
    }

    core.notice(`Synced ${groupedIssues.size} SonarQube backlog issue group(s) for ${sonarProjectKey}.`);
};
