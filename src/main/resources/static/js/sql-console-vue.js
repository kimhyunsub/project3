(function () {
    const { createApp } = Vue;

    function formDataWithCsrf(context, queryText) {
        const formData = new FormData();
        formData.append(context?.csrfParameterName || '_csrf', context?.csrfToken || '');
        formData.append('queryText', queryText || '');
        return formData;
    }

    document.addEventListener('DOMContentLoaded', function () {
        const mountNode = document.getElementById('sql-console-app');
        if (!mountNode || !window.Vue) {
            return;
        }

        createApp({
            data() {
                return {
                    ready: false,
                    loading: false,
                    downloading: false,
                    loadFailed: false,
                    context: null,
                    queryText: '',
                    queryResult: null,
                    errorMessage: ''
                };
            },
            computed: {
                snippets() {
                    return this.context?.sqlSnippets || [];
                },
                workplaceScopedAdmin() {
                    return Boolean(this.context?.workplaceScopedAdmin);
                },
                resultColumnsCountText() {
                    return `컬럼 ${this.queryResult?.columns?.length || 0}개`;
                },
                resultRowsCountText() {
                    if (!this.queryResult) {
                        return '조회 0행';
                    }
                    if (this.queryResult.truncated) {
                        return `미리보기 ${this.queryResult.rows.length}행 (최대 ${this.queryResult.rowLimit}행)`;
                    }
                    return `조회 ${this.queryResult.rows.length}행`;
                }
            },
            async mounted() {
                await this.loadContext();
            },
            methods: {
                async loadContext() {
                    this.loadFailed = false;
                    try {
                        const response = await fetch('/sql-console/page-context', {
                            headers: { 'X-Requested-With': 'XMLHttpRequest' }
                        });
                        if (!response.ok) {
                            throw new Error('SQL 리포트 정보를 불러오지 못했습니다.');
                        }
                        this.context = await response.json();
                        this.ready = true;
                    } catch (error) {
                        this.loadFailed = true;
                    }
                },
                applySnippet(snippet) {
                    this.queryText = snippet.query || '';
                },
                async previewQuery() {
                    this.loading = true;
                    this.errorMessage = '';
                    try {
                        const response = await fetch('/sql-console/query-data', {
                            method: 'POST',
                            headers: { 'X-Requested-With': 'XMLHttpRequest' },
                            body: formDataWithCsrf(this.context, this.queryText)
                        });
                        if (!response.ok) {
                            throw new Error('SQL 미리보기 실행에 실패했습니다.');
                        }
                        const result = await response.json();
                        this.queryResult = result.queryResult;
                        this.errorMessage = result.errorMessage || '';
                    } catch (error) {
                        this.errorMessage = error.message;
                    } finally {
                        this.loading = false;
                    }
                },
                async downloadExcel() {
                    this.downloading = true;
                    this.errorMessage = '';
                    try {
                        const response = await fetch('/sql-console/excel', {
                            method: 'POST',
                            headers: { 'X-Requested-With': 'XMLHttpRequest' },
                            body: formDataWithCsrf(this.context, this.queryText)
                        });
                        if (!response.ok) {
                            throw new Error('엑셀 다운로드에 실패했습니다.');
                        }
                        const blob = await response.blob();
                        const url = window.URL.createObjectURL(blob);
                        const anchor = document.createElement('a');
                        anchor.href = url;
                        anchor.download = 'sql-report.xlsx';
                        document.body.appendChild(anchor);
                        anchor.click();
                        anchor.remove();
                        window.URL.revokeObjectURL(url);
                    } catch (error) {
                        this.errorMessage = error.message;
                    } finally {
                        this.downloading = false;
                    }
                }
            },
            template: `
                <div class="app-shell">
                    <aside class="sidebar">
                        <div>
                            <div class="eyebrow">Attendance Admin</div>
                            <h2>관리자 페이지</h2>
                        </div>
                        <nav class="nav-menu">
                            <a href="/dashboard">오늘 출근 현황</a>
                            <a href="/attendance/monthly">월별 출근 현황</a>
                            <a href="/employees">직원 목록</a>
                            <a href="/settings/location">설정</a>
                            <a href="/sql-console" class="active">SQL 리포트</a>
                        </nav>
                        <form action="/logout" method="post">
                            <input type="hidden" :name="context?.csrfParameterName || '_csrf'" :value="context?.csrfToken || ''">
                            <button type="submit" class="secondary-button full-width">로그아웃</button>
                        </form>
                    </aside>

                    <main class="content">
                        <div v-if="loadFailed" class="alert error">SQL 리포트 정보를 불러오지 못했습니다.</div>
                        <div v-else-if="errorMessage" class="alert error">{{ errorMessage }}</div>

                        <section class="page-header">
                            <div>
                                <div class="eyebrow">SQL Report</div>
                                <h1>SQL 리포트</h1>
                            </div>
                            <div class="page-header-actions">
                                <p>{{ workplaceScopedAdmin ? '사업장 관리자는 본인 사업장으로 제한된 scoped_* 데이터만 조회할 수 있습니다.' : '회사 관리자는 읽기 전용 SQL을 실행하고 결과를 엑셀로 내려받을 수 있습니다.' }}</p>
                                <a class="ghost-link small-link"
                                   href="https://github.com/hsft-team/project1/blob/main/docs/DB_TABLE_SPEC.md"
                                   target="_blank"
                                   rel="noreferrer">
                                    테이블 명세 보기
                                </a>
                            </div>
                        </section>

                        <section class="panel" v-if="ready">
                            <div class="panel-header">
                                <h2>쿼리 실행</h2>
                                <span class="pill neutral">SELECT / WITH 전용</span>
                            </div>
                            <p class="section-copy">한 번에 한 개의 조회 쿼리만 실행할 수 있습니다. 미리보기는 최대 200행, 엑셀 다운로드는 최대 5,000행까지 지원합니다.</p>

                            <div class="sql-console-notice">
                                <strong>제한 사항</strong>
                                <p v-if="!workplaceScopedAdmin">세미콜론, 주석, INSERT/UPDATE/DELETE/DDL 문은 차단됩니다. 운영 DB에 부담이 큰 전체 스캔 쿼리는 피하는 것을 권장합니다.</p>
                                <p v-else>사업장 관리자는 원본 테이블 대신 scoped_employees, scoped_attendance_records, scoped_workplace만 조회할 수 있습니다. 이 범위는 현재 로그인한 사업장으로 자동 제한됩니다.</p>
                            </div>

                            <div class="sql-console-notice" v-if="workplaceScopedAdmin">
                                <strong>예시 쿼리</strong>
                                <p>select employee_code, name from scoped_employees order by name</p>
                                <p>select attendance_date, employee_id, status from scoped_attendance_records order by attendance_date desc</p>
                            </div>

                            <div class="sql-snippet-grid" v-if="snippets.length > 0">
                                <button v-for="snippet in snippets"
                                        :key="snippet.key"
                                        type="button"
                                        class="sql-snippet-card"
                                        @click="applySnippet(snippet)">
                                    <strong>{{ snippet.label }}</strong>
                                    <span>{{ snippet.description }}</span>
                                </button>
                            </div>

                            <form class="settings-form" @submit.prevent="previewQuery">
                                <label>
                                    SQL
                                    <textarea v-model="queryText"
                                              class="sql-editor"
                                              rows="12"
                                              :placeholder="workplaceScopedAdmin ? 'select employee_code, name from scoped_employees order by name' : 'select employee_code, name from employees order by name'"></textarea>
                                </label>
                                <div class="button-row">
                                    <button type="submit" class="primary-button" :disabled="loading">미리보기 실행</button>
                                    <button type="button" class="ghost-link" :disabled="downloading" @click="downloadExcel">엑셀 다운로드</button>
                                </div>
                            </form>
                        </section>

                        <section class="panel" v-if="ready">
                            <div class="panel-header">
                                <h2>조회 결과</h2>
                                <div class="action-row sql-console-meta">
                                    <span class="pill neutral">{{ resultColumnsCountText }}</span>
                                    <span class="pill neutral">{{ resultRowsCountText }}</span>
                                </div>
                            </div>

                            <div v-if="loading" class="empty-state">조회 결과를 불러오는 중입니다.</div>
                            <div v-else-if="!queryResult || !queryResult.rows || queryResult.rows.length === 0" class="empty-state">조회 결과가 없습니다.</div>
                            <div v-else class="table-scroll">
                                <table class="sql-result-table">
                                    <thead>
                                    <tr>
                                        <th v-for="column in queryResult.columns" :key="column">{{ column }}</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <tr v-for="(row, rowIndex) in queryResult.rows" :key="rowIndex">
                                        <td v-for="(cell, cellIndex) in row" :key="rowIndex + '-' + cellIndex">{{ cell }}</td>
                                    </tr>
                                    </tbody>
                                </table>
                            </div>
                        </section>
                    </main>
                </div>
            `
        }).mount('#sql-console-app');
    });
})();
