(function () {
    const { createApp } = Vue;

    function statusClass(status) {
        if (status === 'APPROVED') return 'pill success';
        if (status === 'PENDING') return 'pill warning';
        if (status === 'REJECTED') return 'pill danger';
        return 'pill neutral';
    }

    function toDateKey(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    function createMonthCursor(date = new Date()) {
        return new Date(date.getFullYear(), date.getMonth(), 1);
    }

    document.addEventListener('DOMContentLoaded', function () {
        const mountNode = document.getElementById('work-requests-app');
        if (!mountNode || !window.Vue) {
            return;
        }

        createApp({
            data() {
                return {
                    context: null,
                    requests: [],
                    createForm: {
                        employeeCode: '',
                        requestType: 'VACATION',
                        requestDate: new Date().toISOString().slice(0, 10),
                        halfDayType: 'MORNING',
                        earlyLeaveMinutes: 30,
                        reason: ''
                    },
                    uploadFile: null,
                    uploadFailureMessages: [],
                    calendarCursor: createMonthCursor(),
                    selectedDate: toDateKey(new Date()),
                    loading: false,
                    actingId: null,
                    submittingCreate: false,
                    uploading: false,
                    feedbackMessage: '',
                    feedbackType: 'success',
                    loadFailed: false
                };
            },
            computed: {
                canAccessSqlConsole() {
                    return Boolean(this.context?.canAccessSqlConsole);
                },
                pendingCount() {
                    return this.requests.filter((request) => request.status === 'PENDING').length;
                },
                calendarTitle() {
                    return `${this.calendarCursor.getFullYear()}년 ${this.calendarCursor.getMonth() + 1}월`;
                },
                weekdayLabels() {
                    return ['일', '월', '화', '수', '목', '금', '토'];
                },
                calendarRequestsByDate() {
                    return this.requests.reduce((groups, request) => {
                        if (!request.requestDate) {
                            return groups;
                        }
                        groups[request.requestDate] = groups[request.requestDate] || [];
                        groups[request.requestDate].push(request);
                        return groups;
                    }, {});
                },
                calendarWeeks() {
                    const year = this.calendarCursor.getFullYear();
                    const month = this.calendarCursor.getMonth();
                    const firstDay = new Date(year, month, 1);
                    const firstCell = new Date(firstDay);
                    firstCell.setDate(firstCell.getDate() - firstDay.getDay());

                    const weeks = [];
                    for (let weekIndex = 0; weekIndex < 6; weekIndex++) {
                        const week = [];
                        for (let dayIndex = 0; dayIndex < 7; dayIndex++) {
                            const current = new Date(firstCell);
                            current.setDate(firstCell.getDate() + weekIndex * 7 + dayIndex);
                            const dateKey = toDateKey(current);
                            const requests = this.calendarRequestsByDate[dateKey] || [];
                            week.push({
                                dateKey,
                                day: current.getDate(),
                                inMonth: current.getMonth() === month,
                                today: dateKey === toDateKey(new Date()),
                                selected: dateKey === this.selectedDate,
                                requests,
                                pendingCount: requests.filter((request) => request.status === 'PENDING').length,
                                approvedCount: requests.filter((request) => request.status === 'APPROVED').length,
                                vacationCount: requests.filter((request) => request.requestType === 'VACATION').length,
                                halfDayCount: requests.filter((request) => request.requestType === 'HALF_DAY').length,
                                earlyLeaveCount: requests.filter((request) => request.requestType === 'EARLY_LEAVE').length
                            });
                        }
                        weeks.push(week);
                    }
                    return weeks;
                },
                selectedDateRequests() {
                    return this.calendarRequestsByDate[this.selectedDate] || [];
                },
                monthSummary() {
                    const monthPrefix = `${this.calendarCursor.getFullYear()}-${String(this.calendarCursor.getMonth() + 1).padStart(2, '0')}`;
                    const monthRequests = this.requests.filter((request) => request.requestDate?.startsWith(monthPrefix));
                    return {
                        total: monthRequests.length,
                        vacation: monthRequests.filter((request) => request.requestType === 'VACATION').length,
                        halfDay: monthRequests.filter((request) => request.requestType === 'HALF_DAY').length,
                        earlyLeave: monthRequests.filter((request) => request.requestType === 'EARLY_LEAVE').length,
                        pending: monthRequests.filter((request) => request.status === 'PENDING').length
                    };
                }
            },
            async mounted() {
                await this.loadContext();
                await this.loadRequests();
            },
            methods: {
                showFeedback(message, type = 'success') {
                    this.feedbackMessage = message;
                    this.feedbackType = type;
                },
                async loadContext() {
                    const response = await fetch('/work-requests/page-context', {
                        headers: { 'X-Requested-With': 'XMLHttpRequest' }
                    });
                    if (!response.ok) {
                        throw new Error('근무 신청 화면 정보를 불러오지 못했습니다.');
                    }
                    this.context = await response.json();
                },
                async loadRequests() {
                    this.loading = true;
                    this.loadFailed = false;
                    try {
                        const response = await fetch('/work-requests/data', {
                            headers: { 'X-Requested-With': 'XMLHttpRequest' }
                        });
                        if (!response.ok) {
                            throw new Error('근무 신청 목록을 불러오지 못했습니다.');
                        }
                        const requests = await response.json();
                        this.requests = Array.isArray(requests)
                            ? requests.filter((request) => request.status !== 'CANCELED')
                            : [];
                    } catch (error) {
                        this.loadFailed = true;
                    } finally {
                        this.loading = false;
                    }
                },
                async reviewRequest(requestId, action) {
                    const actionLabel = action === 'approve' ? '승인' : action === 'reject' ? '반려' : '취소';
                    const reviewNote = window.prompt(
                        action === 'approve'
                            ? '승인 메모가 있으면 입력해 주세요.'
                            : action === 'reject'
                                ? '반려 사유를 입력해 주세요.'
                                : '취소 사유가 있으면 입력해 주세요.',
                        ''
                    );
                    if (reviewNote === null) {
                        return;
                    }

                    this.actingId = requestId;
                    try {
                        const body = new URLSearchParams();
                        body.set('reviewNote', reviewNote);
                        body.set(this.context?.csrfParameterName || '_csrf', this.context?.csrfToken || '');

                        const response = await fetch(`/work-requests/${requestId}/${action}`, {
                            method: 'POST',
                            headers: { 'X-Requested-With': 'XMLHttpRequest' },
                            body
                        });
                        const result = await response.json();
                        if (!response.ok || !result.success) {
                            throw new Error(result.message || `${actionLabel} 처리에 실패했습니다.`);
                        }
                        this.showFeedback(result.message, 'success');
                        await this.loadRequests();
                    } catch (error) {
                        this.showFeedback(error.message, 'error');
                    } finally {
                        this.actingId = null;
                    }
                },
                async submitCreate() {
                    this.submittingCreate = true;
                    try {
                        const body = new URLSearchParams();
                        body.set('employeeCode', this.createForm.employeeCode);
                        body.set('requestType', this.createForm.requestType);
                        body.set('requestDate', this.createForm.requestDate);
                        body.set('halfDayType', this.createForm.requestType === 'HALF_DAY' ? this.createForm.halfDayType : '');
                        body.set('earlyLeaveMinutes', this.createForm.requestType === 'EARLY_LEAVE' ? String(this.createForm.earlyLeaveMinutes || '') : '');
                        body.set('reason', this.createForm.reason || '');
                        body.set(this.context?.csrfParameterName || '_csrf', this.context?.csrfToken || '');

                        const response = await fetch('/work-requests/create', {
                            method: 'POST',
                            headers: { 'X-Requested-With': 'XMLHttpRequest' },
                            body
                        });
                        const result = await response.json();
                        if (!response.ok || !result.success) {
                            throw new Error(result.message || '근무 신청 등록에 실패했습니다.');
                        }
                        this.createForm = {
                            employeeCode: '',
                            requestType: 'VACATION',
                            requestDate: new Date().toISOString().slice(0, 10),
                            halfDayType: 'MORNING',
                            earlyLeaveMinutes: 30,
                            reason: ''
                        };
                        this.showFeedback(result.message, 'success');
                        await this.loadRequests();
                    } catch (error) {
                        this.showFeedback(error.message, 'error');
                    } finally {
                        this.submittingCreate = false;
                    }
                },
                onUploadFileChange(event) {
                    this.uploadFile = event.target.files?.[0] || null;
                },
                async submitUpload() {
                    if (!this.uploadFile) {
                        this.showFeedback('업로드할 엑셀 파일을 선택해 주세요.', 'error');
                        return;
                    }

                    this.uploading = true;
                    this.uploadFailureMessages = [];
                    try {
                        const body = new FormData();
                        body.append('workRequestFile', this.uploadFile);
                        body.append(this.context?.csrfParameterName || '_csrf', this.context?.csrfToken || '');

                        const response = await fetch('/work-requests/upload', {
                            method: 'POST',
                            headers: { 'X-Requested-With': 'XMLHttpRequest' },
                            body
                        });
                        const result = await response.json();
                        if (!response.ok) {
                            throw new Error(result.message || '엑셀 업로드에 실패했습니다.');
                        }
                        this.uploadFailureMessages = result.failureMessages || [];
                        this.showFeedback(result.message, result.success ? 'success' : 'error');
                        await this.loadRequests();
                    } catch (error) {
                        this.showFeedback(error.message, 'error');
                    } finally {
                        this.uploading = false;
                    }
                },
                moveMonth(offset) {
                    this.calendarCursor = new Date(
                        this.calendarCursor.getFullYear(),
                        this.calendarCursor.getMonth() + offset,
                        1
                    );
                },
                goToday() {
                    const today = new Date();
                    this.calendarCursor = createMonthCursor(today);
                    this.selectedDate = toDateKey(today);
                },
                selectCalendarDate(day) {
                    this.selectedDate = day.dateKey;
                    if (!day.inMonth) {
                        const next = new Date(day.dateKey + 'T00:00:00');
                        this.calendarCursor = createMonthCursor(next);
                    }
                },
                requestDetailText(request) {
                    return request.halfDayTypeLabel || (request.earlyLeaveMinutes ? request.earlyLeaveMinutes + '분 유연근무' : '-');
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
                            <a href="/attendance/monthly">월별 출근 현황</a>
                            <a href="/work-requests" class="active">근무 신청 관리</a>
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
                                <div class="eyebrow">Work Requests</div>
                                <h1>근무 신청 관리</h1>
                            </div>
                            <p>{{ context?.approvalRequired ? '직원 신청은 승인 후 확정됩니다.' : '현재는 승인 없이 신청 즉시 확정됩니다.' }}</p>
                        </section>

                        <div v-if="feedbackMessage" :class="['alert', feedbackType === 'error' ? 'error' : 'success']">{{ feedbackMessage }}</div>
                        <div v-if="loadFailed" class="alert error">근무 신청 목록을 불러오지 못했습니다.</div>

                        <section class="panel work-calendar-panel">
                            <div class="panel-header">
                                <div>
                                    <h2>신청 달력</h2>
                                    <p class="section-copy">날짜별 휴가, 반차, 유연근무 신청을 월 단위로 확인합니다.</p>
                                </div>
                                <div class="button-row">
                                    <button type="button" class="ghost-link" @click="moveMonth(-1)">이전</button>
                                    <strong class="calendar-title">{{ calendarTitle }}</strong>
                                    <button type="button" class="ghost-link" @click="moveMonth(1)">다음</button>
                                    <button type="button" class="primary-button small-primary" @click="goToday">오늘</button>
                                </div>
                            </div>

                            <div class="calendar-summary-row">
                                <span class="pill neutral">전체 {{ monthSummary.total }}건</span>
                                <span class="pill success">휴가 {{ monthSummary.vacation }}건</span>
                                <span class="pill warning">반차 {{ monthSummary.halfDay }}건</span>
                                <span class="pill neutral">유연근무 {{ monthSummary.earlyLeave }}건</span>
                                <span class="pill danger">대기 {{ monthSummary.pending }}건</span>
                            </div>

                            <div class="work-calendar-layout">
                                <div class="work-calendar-grid">
                                    <div class="calendar-weekday" v-for="label in weekdayLabels" :key="label">{{ label }}</div>
                                    <template v-for="(week, weekIndex) in calendarWeeks" :key="'week-' + weekIndex">
                                        <button
                                            v-for="day in week"
                                            :key="day.dateKey"
                                            type="button"
                                            :class="['calendar-day', !day.inMonth ? 'muted' : '', day.today ? 'today' : '', day.selected ? 'selected' : '']"
                                            @click="selectCalendarDate(day)">
                                            <span class="calendar-day-number">{{ day.day }}</span>
                                            <span class="calendar-day-total" v-if="day.requests.length">{{ day.requests.length }}건</span>
                                            <span class="calendar-day-badges" v-if="day.requests.length">
                                                <span v-if="day.vacationCount" class="calendar-dot vacation">휴 {{ day.vacationCount }}</span>
                                                <span v-if="day.halfDayCount" class="calendar-dot half">반 {{ day.halfDayCount }}</span>
                                                <span v-if="day.earlyLeaveCount" class="calendar-dot flex">유 {{ day.earlyLeaveCount }}</span>
                                            </span>
                                            <span class="calendar-pending" v-if="day.pendingCount">대기 {{ day.pendingCount }}</span>
                                        </button>
                                    </template>
                                </div>

                                <aside class="calendar-detail">
                                    <div class="calendar-detail-header">
                                        <h3>{{ selectedDate }}</h3>
                                        <span class="pill neutral">{{ selectedDateRequests.length }}건</span>
                                    </div>
                                    <div class="empty-state" v-if="selectedDateRequests.length === 0">선택한 날짜의 신청이 없습니다.</div>
                                    <div v-else class="calendar-detail-list">
                                        <article class="calendar-detail-item" v-for="request in selectedDateRequests" :key="'detail-' + request.id">
                                            <div>
                                                <strong>{{ request.employeeName }} <small>{{ request.employeeCode }}</small></strong>
                                                <p>{{ request.requestTypeLabel }} · {{ requestDetailText(request) }}</p>
                                                <p>{{ request.reason || '사유 없음' }}</p>
                                            </div>
                                            <span :class="statusClass(request.status)">{{ request.statusLabel }}</span>
                                        </article>
                                    </div>
                                </aside>
                            </div>
                        </section>

                        <section class="panel">
                            <div class="panel-header">
                                <h2>관리자 직접 등록</h2>
                                <span class="pill success">즉시 승인</span>
                            </div>
                            <form class="settings-form compact-settings-form" @submit.prevent="submitCreate">
                                <div class="form-row">
                                    <label>
                                        사번
                                        <input v-model="createForm.employeeCode" type="text" placeholder="EMP001">
                                    </label>
                                    <label>
                                        날짜
                                        <input v-model="createForm.requestDate" type="date">
                                    </label>
                                </div>
                                <div class="form-row">
                                    <label>
                                        유형
                                        <select v-model="createForm.requestType">
                                            <option value="VACATION">휴가</option>
                                            <option value="HALF_DAY">반차</option>
                                            <option value="EARLY_LEAVE">유연근무</option>
                                        </select>
                                    </label>
                                    <label v-if="createForm.requestType === 'HALF_DAY'">
                                        반차 구분
                                        <select v-model="createForm.halfDayType">
                                            <option value="MORNING">오전 반차</option>
                                            <option value="AFTERNOON">오후 반차</option>
                                        </select>
                                    </label>
                                    <label v-if="createForm.requestType === 'EARLY_LEAVE'">
                                        유연근무 시간
                                        <select v-model.number="createForm.earlyLeaveMinutes">
                                            <option :value="30">30분</option>
                                            <option :value="60">1시간</option>
                                            <option :value="90">1시간 30분</option>
                                            <option :value="120">2시간</option>
                                            <option :value="180">3시간</option>
                                            <option :value="240">4시간</option>
                                        </select>
                                    </label>
                                </div>
                                <label>
                                    사유
                                    <textarea v-model="createForm.reason" rows="3" placeholder="필요한 경우 메모를 남겨 주세요."></textarea>
                                </label>
                                <div class="button-row button-row-end">
                                    <button type="submit" class="primary-button" :disabled="submittingCreate">
                                        {{ submittingCreate ? '등록 중...' : '신청 등록' }}
                                    </button>
                                </div>
                            </form>
                        </section>

                        <section class="panel">
                            <div class="panel-header">
                                <h2>엑셀 일괄 추가</h2>
                                <a class="ghost-link" href="/work-requests/upload-template">샘플 엑셀 다운로드</a>
                            </div>
                            <p class="section-copy">첫 번째 시트에 사번, 날짜, 유형, 반차구분, 유연근무분, 사유 순서로 입력해 주세요. 유형은 휴가, 반차, 유연근무를 사용할 수 있습니다.</p>
                            <form class="upload-form" @submit.prevent="submitUpload">
                                <label>
                                    엑셀 파일 선택
                                    <input type="file" accept=".xlsx" @change="onUploadFileChange">
                                </label>
                                <div class="button-row">
                                    <button type="submit" class="primary-button" :disabled="uploading">
                                        {{ uploading ? '업로드 중...' : '엑셀 업로드' }}
                                    </button>
                                </div>
                            </form>
                            <div class="alert error" v-if="uploadFailureMessages.length">
                                <p v-for="message in uploadFailureMessages" :key="message">{{ message }}</p>
                            </div>
                        </section>

                        <section class="panel">
                            <div class="panel-header">
                                <h2>신청 목록</h2>
                                <span class="pill warning">대기 {{ pendingCount }}건</span>
                            </div>
                            <p class="section-copy" v-if="context?.workplaceScopedAdmin">사업장 관리자는 담당 사업장 직원의 신청만 확인할 수 있습니다.</p>
                            <p class="section-copy" v-else>휴가, 반차, 유연근무 신청을 한 곳에서 승인하거나 반려할 수 있습니다.</p>

                            <div class="empty-state" v-if="!loading && requests.length === 0">등록된 근무 신청이 없습니다.</div>
                            <div class="table-scroll" v-else>
                                <table class="data-table">
                                    <thead>
                                    <tr>
                                        <th>신청일</th>
                                        <th>직원</th>
                                        <th>사업장</th>
                                        <th>유형</th>
                                        <th>상세</th>
                                        <th>상태</th>
                                        <th>사유</th>
                                        <th>검토</th>
                                        <th>처리</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <tr v-for="request in requests" :key="request.id">
                                        <td>{{ request.requestDate }}</td>
                                        <td>{{ request.employeeName }}<br><small>{{ request.employeeCode }}</small></td>
                                        <td>{{ request.workplaceName }}</td>
                                        <td>{{ request.requestTypeLabel }}</td>
                                        <td>{{ requestDetailText(request) }}</td>
                                        <td><span :class="statusClass(request.status)">{{ request.statusLabel }}</span></td>
                                        <td>{{ request.reason || '-' }}</td>
                                        <td>{{ request.reviewedByName ? request.reviewedByName + ' (' + request.reviewedByEmployeeCode + ')' : '-' }}</td>
                                        <td>
                                            <div class="button-row" v-if="request.status === 'PENDING'">
                                                <button type="button" class="primary-button small-primary" :disabled="actingId === request.id" @click="reviewRequest(request.id, 'approve')">승인</button>
                                                <button type="button" class="ghost-link" :disabled="actingId === request.id" @click="reviewRequest(request.id, 'reject')">반려</button>
                                            </div>
                                            <div class="button-row" v-else-if="request.status === 'APPROVED'">
                                                <button type="button" class="ghost-link" :disabled="actingId === request.id" @click="reviewRequest(request.id, 'cancel')">취소</button>
                                            </div>
                                            <span v-else>-</span>
                                        </td>
                                    </tr>
                                    </tbody>
                                </table>
                            </div>
                        </section>
                    </main>
                </div>
            `
        }).mount('#work-requests-app');
    });
})();
