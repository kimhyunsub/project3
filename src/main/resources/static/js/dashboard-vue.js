(function () {
    const { createApp } = Vue;

    const FILTER_LABELS = {
        ALL: '전체 직원',
        PRESENT: '정상 출근',
        LATE: '지각',
        ABSENT: '미출근',
        CHECKED_OUT: '퇴근 완료'
    };

    const STATE_LABELS = {
        WORKING: '출근',
        LATE: '지각',
        ABSENT: '미출근',
        CHECKED_OUT: '퇴근'
    };

    function workplaceParamValue(workplaceId) {
        return workplaceId === null || workplaceId === undefined || workplaceId === '' ? '' : String(workplaceId);
    }

    function buildQuery(state) {
        const params = new URLSearchParams();
        params.set('filter', state.filter || 'ALL');
        if (workplaceParamValue(state.workplaceId) !== '') {
            params.set('workplaceId', workplaceParamValue(state.workplaceId));
        }
        return params;
    }

    function parseInitialState() {
        const params = new URLSearchParams(window.location.search);
        return {
            filter: params.get('filter') || 'ALL',
            workplaceId: params.get('workplaceId') || ''
        };
    }

    function statusClass(stateName) {
        if (stateName === 'WORKING') return 'pill success';
        if (stateName === 'LATE') return 'pill warning';
        if (stateName === 'ABSENT') return 'pill danger';
        return 'pill neutral';
    }

    document.addEventListener('DOMContentLoaded', function () {
        const mountNode = document.getElementById('dashboard-app');
        if (!mountNode || !window.Vue) {
            return;
        }

        createApp({
            data() {
                return {
                    ready: false,
                    loading: false,
                    loadFailed: false,
                    context: null,
                    state: parseInitialState(),
                    dashboard: {
                        summary: {
                            totalEmployees: 0,
                            presentCount: 0,
                            lateCount: 0,
                            absentCount: 0,
                            checkedOutCount: 0
                        },
                        attendances: []
                    }
                };
            },
            computed: {
                canAccessSqlConsole() {
                    return Boolean(this.context?.canAccessSqlConsole);
                },
                workplaceOptions() {
                    return this.context?.workplaceOptions || [];
                },
                selectedFilterLabel() {
                    return FILTER_LABELS[this.state.filter] || '전체 직원';
                },
                listTitle() {
                    return `${this.selectedFilterLabel} 목록`;
                },
                listCountText() {
                    return `총 ${this.dashboard.attendances.length}명`;
                },
                pageDescription() {
                    return this.context?.workplaceScopedAdmin
                        ? '담당 사업장 직원들의 출근 상태를 한눈에 확인할 수 있습니다.'
                        : '직원들의 출근 상태를 한눈에 확인할 수 있습니다.';
                },
                summaryCards() {
                    return [
                        { key: 'ALL', label: '전체 직원', value: this.dashboard.summary.totalEmployees, tone: '' },
                        { key: 'PRESENT', label: '정상 출근', value: this.dashboard.summary.presentCount, tone: '' },
                        { key: 'LATE', label: '지각', value: this.dashboard.summary.lateCount, tone: 'warning' },
                        { key: 'ABSENT', label: '미출근', value: this.dashboard.summary.absentCount, tone: 'danger' },
                        { key: 'CHECKED_OUT', label: '퇴근 완료', value: this.dashboard.summary.checkedOutCount, tone: '' }
                    ];
                },
                showResetFilter() {
                    return this.state.filter !== 'ALL';
                }
            },
            async mounted() {
                await this.loadContext();
                await this.loadDashboard();
            },
            methods: {
                syncLocation() {
                    window.history.replaceState({}, '', `/app/dashboard.html?${buildQuery(this.state).toString()}`);
                },
                async loadContext() {
                    const response = await fetch(`/dashboard/page-context?${buildQuery(this.state).toString()}`, {
                        headers: { 'X-Requested-With': 'XMLHttpRequest' }
                    });
                    if (!response.ok) {
                        throw new Error('대시보드 컨텍스트 조회 실패');
                    }
                    this.context = await response.json();
                    this.state.filter = this.context.selectedFilter || this.state.filter;
                    this.state.workplaceId = workplaceParamValue(this.context.selectedWorkplaceId);
                    this.ready = true;
                },
                async loadDashboard(nextState) {
                    if (nextState) {
                        this.state.filter = nextState.filter;
                        this.state.workplaceId = workplaceParamValue(nextState.workplaceId);
                    }

                    this.loading = true;
                    this.loadFailed = false;
                    try {
                        const response = await fetch(`/dashboard/data?${buildQuery(this.state).toString()}`, {
                            headers: { 'X-Requested-With': 'XMLHttpRequest' }
                        });
                        if (!response.ok) {
                            throw new Error('오늘 출근 현황을 불러오지 못했습니다.');
                        }
                        this.dashboard = await response.json();
                        this.syncLocation();
                    } catch (error) {
                        this.loadFailed = true;
                    } finally {
                        this.loading = false;
                    }
                },
                applyWorkplaceFilter() {
                    this.loadDashboard({
                        filter: this.state.filter,
                        workplaceId: this.state.workplaceId
                    });
                },
                selectFilter(filter) {
                    this.loadDashboard({
                        filter,
                        workplaceId: this.state.workplaceId
                    });
                },
                attendanceLabel(stateName) {
                    return STATE_LABELS[stateName] || stateName || '-';
                },
                statusClass
            },
            template: `
                <div class="app-shell">
                    <aside class="sidebar">
                        <div>
                            <div class="eyebrow">Attendance Admin</div>
                            <h2>관리자 페이지</h2>
                        </div>
                        <nav class="nav-menu">
                            <a href="/dashboard" class="active">오늘 출근 현황</a>
                            <a href="/attendance/monthly">월별 출근 현황</a>
                            <a href="/employees">직원 목록</a>
                            <a href="/settings/location">설정</a>
                            <a v-if="canAccessSqlConsole" href="/sql-console">SQL 리포트</a>
                        </nav>
                        <form action="/logout" method="post">
                            <input type="hidden" :name="context?.csrfParameterName || '_csrf'" :value="context?.csrfToken || ''">
                            <button type="submit" class="secondary-button full-width">로그아웃</button>
                        </form>
                    </aside>

                    <main class="content">
                        <section class="page-header">
                            <div>
                                <div class="eyebrow">Dashboard</div>
                                <h1>오늘 출근 현황</h1>
                            </div>
                            <p>{{ pageDescription }}</p>
                        </section>

                        <div class="alert error" v-if="loadFailed">오늘 출근 현황을 불러오지 못했습니다.</div>

                        <section class="panel" v-if="ready">
                            <div class="panel-header">
                                <h2>사업장 필터</h2>
                            </div>
                            <form class="filter-form" @submit.prevent="applyWorkplaceFilter">
                                <label>
                                    사업장
                                    <select v-model="state.workplaceId">
                                        <option v-if="!context?.workplaceScopedAdmin" value="">전체</option>
                                        <option v-if="!context?.workplaceScopedAdmin" value="0">본사</option>
                                        <option v-for="workplace in workplaceOptions" :key="workplace.id" :value="String(workplace.id)">{{ workplace.name }}</option>
                                    </select>
                                </label>
                                <button v-if="!context?.workplaceScopedAdmin" type="submit" class="primary-button">적용</button>
                            </form>
                        </section>

                        <section class="card-grid">
                            <button v-for="card in summaryCards"
                                    :key="card.key"
                                    type="button"
                                    class="stat-link"
                                    @click="selectFilter(card.key)">
                                <article :class="['stat-card', card.tone, state.filter === card.key ? 'selected' : '']">
                                    <span>{{ card.label }}</span>
                                    <strong>{{ card.value }}</strong>
                                </article>
                            </button>
                        </section>

                        <section class="panel">
                            <div class="panel-header">
                                <h2>{{ listTitle }}</h2>
                                <div class="button-row">
                                    <span class="pill neutral">{{ listCountText }}</span>
                                    <button v-if="showResetFilter" type="button" class="ghost-link small-link" @click="selectFilter('ALL')">전체 보기</button>
                                </div>
                            </div>
                            <div class="empty-state" v-if="!loading && dashboard.attendances.length === 0">선택한 조건에 해당하는 직원이 없습니다.</div>
                            <table v-else>
                                <thead>
                                <tr>
                                    <th>사번</th>
                                    <th>이름</th>
                                    <th>사업장</th>
                                    <th>권한</th>
                                    <th>상태</th>
                                    <th>출근 시간</th>
                                    <th>퇴근 시간</th>
                                    <th>메모</th>
                                </tr>
                                </thead>
                                <tbody>
                                <tr v-if="loading">
                                    <td colspan="8" class="empty-state-cell">오늘 출근 현황을 불러오는 중입니다.</td>
                                </tr>
                                <tr v-for="record in dashboard.attendances" :key="record.employeeCode">
                                    <td>{{ record.employeeCode }}</td>
                                    <td>{{ record.employeeName }}</td>
                                    <td>{{ record.workplaceName }}</td>
                                    <td>{{ record.role }}</td>
                                    <td><span :class="statusClass(record.state)">{{ attendanceLabel(record.state) }}</span></td>
                                    <td>{{ record.checkInTime }}</td>
                                    <td>{{ record.checkOutTime }}</td>
                                    <td>{{ record.note }}</td>
                                </tr>
                                </tbody>
                            </table>
                        </section>
                    </main>
                </div>
            `
        }).mount('#dashboard-app');
    });
})();
