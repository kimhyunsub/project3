(function () {
    const { createApp } = Vue;

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
        params.set('year', String(state.year));
        params.set('month', String(state.month));
        if (state.employeeCode) {
            params.set('employeeCode', state.employeeCode);
        }
        if (workplaceParamValue(state.workplaceId) !== '') {
            params.set('workplaceId', workplaceParamValue(state.workplaceId));
        }
        return params;
    }

    function parseInitialState() {
        const now = new Date();
        const params = new URLSearchParams(window.location.search);
        return {
            year: Number(params.get('year') || now.getFullYear()),
            month: Number(params.get('month') || (now.getMonth() + 1)),
            employeeCode: params.get('employeeCode') || '',
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
        const mountNode = document.getElementById('monthly-attendance-app');
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
                    data: {
                        summary: {
                            monthLabel: '',
                            totalEmployees: 0,
                            attendedEmployees: 0,
                            attendanceCount: 0,
                            lateCount: 0,
                            checkedOutCount: 0
                        },
                        employees: [],
                        records: [],
                        selectedEmployeeDetail: null
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
                pageDescription() {
                    const label = this.data.summary.monthLabel || `${this.state.year}년 ${this.state.month}월`;
                    return this.context?.workplaceScopedAdmin
                        ? `${label} 담당 사업장 출근 데이터를 직원별로 살펴볼 수 있습니다.`
                        : `${label} 출근 데이터를 직원별로 살펴볼 수 있습니다.`;
                },
                employeesCountText() {
                    return `총 ${this.data.employees.length}명`;
                },
                recordsCountText() {
                    return `총 ${this.data.records.length}건`;
                },
                excelDownloadHref() {
                    return `/attendance/monthly/excel?${buildQuery({
                        year: this.state.year,
                        month: this.state.month,
                        employeeCode: '',
                        workplaceId: this.state.workplaceId
                    }).toString()}`;
                },
                detailOpen() {
                    return Boolean(this.data.selectedEmployeeDetail);
                }
            },
            async mounted() {
                await this.loadContext();
                await this.loadMonthlyData();
            },
            updated() {
                document.body.classList.toggle('modal-open', this.detailOpen);
            },
            beforeUnmount() {
                document.body.classList.remove('modal-open');
            },
            methods: {
                syncLocation() {
                    window.history.replaceState({}, '', `/app/monthly-attendance.html?${buildQuery(this.state).toString()}`);
                },
                async loadContext() {
                    const response = await fetch(`/attendance/monthly/page-context?${buildQuery(this.state).toString()}`, {
                        headers: { 'X-Requested-With': 'XMLHttpRequest' }
                    });
                    if (!response.ok) {
                        throw new Error('월별 출근현황 컨텍스트 조회 실패');
                    }
                    this.context = await response.json();
                    this.state.year = Number(this.context.year || this.state.year);
                    this.state.month = Number(this.context.month || this.state.month);
                    this.state.employeeCode = this.context.selectedEmployeeCode || '';
                    this.state.workplaceId = workplaceParamValue(this.context.selectedWorkplaceId);
                    this.ready = true;
                },
                async loadMonthlyData(nextState) {
                    if (nextState) {
                        this.state.year = Number(nextState.year);
                        this.state.month = Number(nextState.month);
                        this.state.employeeCode = nextState.employeeCode || '';
                        this.state.workplaceId = workplaceParamValue(nextState.workplaceId);
                    }

                    this.loading = true;
                    this.loadFailed = false;
                    try {
                        const response = await fetch(`/attendance/monthly/data?${buildQuery(this.state).toString()}`, {
                            headers: { 'X-Requested-With': 'XMLHttpRequest' }
                        });
                        if (!response.ok) {
                            throw new Error('월별 출근현황을 불러오지 못했습니다.');
                        }
                        this.data = await response.json();
                        this.syncLocation();
                    } catch (error) {
                        this.loadFailed = true;
                    } finally {
                        this.loading = false;
                    }
                },
                submitFilters() {
                    this.loadMonthlyData({
                        year: this.state.year,
                        month: this.state.month,
                        employeeCode: '',
                        workplaceId: this.state.workplaceId
                    });
                },
                openEmployeeDetail(employeeCode) {
                    this.loadMonthlyData({
                        year: this.state.year,
                        month: this.state.month,
                        employeeCode,
                        workplaceId: this.state.workplaceId
                    });
                },
                closeEmployeeDetail() {
                    this.loadMonthlyData({
                        year: this.state.year,
                        month: this.state.month,
                        employeeCode: '',
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
                            <a href="/dashboard">오늘 출근 현황</a>
                            <a href="/attendance/monthly" class="active">월별 출근 현황</a>
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
                        <div class="alert error" v-if="loadFailed">월별 출근 현황을 불러오지 못했습니다.</div>
                        <section class="page-header">
                            <div>
                                <div class="eyebrow">Monthly Report</div>
                                <h1>월별 출근 현황</h1>
                            </div>
                            <p>{{ pageDescription }}</p>
                        </section>

                        <section class="panel" v-if="ready">
                            <div class="panel-header">
                                <h2>조회 월 선택</h2>
                                <div class="action-row">
                                    <span class="pill neutral">{{ data.summary.monthLabel }}</span>
                                    <a class="ghost-link" :href="excelDownloadHref">엑셀 다운로드</a>
                                </div>
                            </div>
                            <form class="filter-form" @submit.prevent="submitFilters">
                                <label>
                                    연도
                                    <input type="number" min="2000" max="2100" v-model="state.year">
                                </label>
                                <label>
                                    월
                                    <input type="number" min="1" max="12" v-model="state.month">
                                </label>
                                <label>
                                    사업장
                                    <select v-model="state.workplaceId">
                                        <option v-if="!context?.workplaceScopedAdmin" value="">전체</option>
                                        <option v-if="!context?.workplaceScopedAdmin" value="0">본사</option>
                                        <option v-for="workplace in workplaceOptions" :key="workplace.id" :value="String(workplace.id)">{{ workplace.name }}</option>
                                    </select>
                                </label>
                                <button v-if="!context?.workplaceScopedAdmin" type="submit" class="primary-button">조회하기</button>
                            </form>
                        </section>

                        <section class="card-grid">
                            <article class="stat-card">
                                <span>전체 직원</span>
                                <strong>{{ data.summary.totalEmployees }}</strong>
                            </article>
                            <article class="stat-card">
                                <span>출근한 직원</span>
                                <strong>{{ data.summary.attendedEmployees }}</strong>
                            </article>
                            <article class="stat-card">
                                <span>총 출근 기록</span>
                                <strong>{{ data.summary.attendanceCount }}</strong>
                            </article>
                            <article class="stat-card warning">
                                <span>지각 기록</span>
                                <strong>{{ data.summary.lateCount }}</strong>
                            </article>
                            <article class="stat-card">
                                <span>퇴근 완료</span>
                                <strong>{{ data.summary.checkedOutCount }}</strong>
                            </article>
                        </section>

                        <section class="panel">
                            <div class="panel-header">
                                <h2>직원별 월간 요약</h2>
                                <span class="pill neutral">{{ employeesCountText }}</span>
                            </div>
                            <table>
                                <thead>
                                <tr>
                                    <th>사번</th>
                                    <th>이름</th>
                                    <th>사업장</th>
                                    <th>권한</th>
                                    <th>출근일수</th>
                                    <th>지각일수</th>
                                    <th>퇴근완료</th>
                                    <th>최근 출근일</th>
                                    <th>최근 상태</th>
                                </tr>
                                </thead>
                                <tbody>
                                <tr v-if="loading">
                                    <td colspan="9" class="empty-state-cell">월별 출근 현황을 불러오는 중입니다.</td>
                                </tr>
                                <tr v-for="employee in data.employees" :key="employee.employeeCode">
                                    <td>{{ employee.employeeCode }}</td>
                                    <td><button type="button" class="employee-name-link" @click="openEmployeeDetail(employee.employeeCode)">{{ employee.employeeName }}</button></td>
                                    <td>{{ employee.workplaceName }}</td>
                                    <td>{{ employee.role }}</td>
                                    <td>{{ employee.attendanceDays }}</td>
                                    <td>{{ employee.lateDays }}</td>
                                    <td>{{ employee.checkedOutDays }}</td>
                                    <td>{{ employee.lastAttendanceDate }}</td>
                                    <td><span :class="statusClass(employee.lastState)">{{ attendanceLabel(employee.lastState) }}</span></td>
                                </tr>
                                </tbody>
                            </table>
                        </section>

                        <section class="panel">
                            <div class="panel-header">
                                <h2>월간 출근 기록 상세</h2>
                                <span class="pill neutral">{{ recordsCountText }}</span>
                            </div>
                            <div class="empty-state" v-if="!loading && data.records.length === 0">선택한 월의 출근 기록이 아직 없습니다.</div>
                            <div class="monthly-records-scroll" v-else>
                                <table>
                                    <thead>
                                    <tr>
                                        <th>날짜</th>
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
                                    <tr v-for="record in data.records" :key="record.attendanceDate + '-' + record.employeeCode + '-' + record.checkInTime">
                                        <td>{{ record.attendanceDate }}</td>
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
                            </div>
                        </section>
                    </main>

                    <div class="modal-sheet employee-detail-modal" v-if="detailOpen" @click.self="closeEmployeeDetail">
                        <article class="employee-detail-card employee-detail-modal-card">
                            <div class="panel-header employee-detail-modal-header">
                                <div>
                                    <div class="eyebrow">Attendance Detail</div>
                                    <h2>직원별 월간 출근 상세</h2>
                                </div>
                                <button type="button" class="ghost-link" @click="closeEmployeeDetail">닫기</button>
                            </div>
                            <div class="employee-detail-header">
                                <div>
                                    <h3>
                                        <span>{{ data.selectedEmployeeDetail.employeeName }}</span>
                                        <small>{{ data.selectedEmployeeDetail.employeeCode }}</small>
                                    </h3>
                                    <p>
                                        <span>{{ data.selectedEmployeeDetail.workplaceName }}</span>
                                        <span> · </span>
                                        <span>{{ data.selectedEmployeeDetail.role }}</span>
                                    </p>
                                </div>
                                <div class="employee-detail-metrics">
                                    <span class="pill neutral">출근 {{ data.selectedEmployeeDetail.attendanceDays }}일</span>
                                    <span class="pill warning">지각 {{ data.selectedEmployeeDetail.lateDays }}일</span>
                                    <span class="pill success">퇴근완료 {{ data.selectedEmployeeDetail.checkedOutDays }}일</span>
                                </div>
                            </div>
                            <div class="modal-table-scroll">
                                <table class="detail-table">
                                    <thead>
                                    <tr>
                                        <th>날짜</th>
                                        <th>상태</th>
                                        <th>출근 시간</th>
                                        <th>퇴근 시간</th>
                                        <th>메모</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <tr v-for="record in data.selectedEmployeeDetail.records" :key="record.attendanceDate + '-' + record.checkInTime">
                                        <td>{{ record.attendanceDate }}</td>
                                        <td><span :class="statusClass(record.state)">{{ attendanceLabel(record.state) }}</span></td>
                                        <td>{{ record.checkInTime }}</td>
                                        <td>{{ record.checkOutTime }}</td>
                                        <td>{{ record.note }}</td>
                                    </tr>
                                    </tbody>
                                </table>
                            </div>
                        </article>
                    </div>
                </div>
            `
        }).mount('#monthly-attendance-app');
    });
})();
