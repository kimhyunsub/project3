(function () {
    const { createApp } = Vue;

    function statusClass(status) {
        if (status === 'APPROVED') return 'pill success';
        if (status === 'PENDING') return 'pill warning';
        if (status === 'REJECTED') return 'pill danger';
        return 'pill neutral';
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
                        this.requests = await response.json();
                    } catch (error) {
                        this.loadFailed = true;
                    } finally {
                        this.loading = false;
                    }
                },
                async reviewRequest(requestId, action) {
                    const reviewNote = window.prompt(
                        action === 'approve' ? '승인 메모가 있으면 입력해 주세요.' : '반려 사유를 입력해 주세요.',
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
                            throw new Error(result.message || '요청 처리에 실패했습니다.');
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
                                            <option value="EARLY_LEAVE">유연근무(조기퇴근)</option>
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
                                        조기퇴근 시간
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
                                        {{ submittingCreate ? '등록 중...' : '근무 신청 등록' }}
                                    </button>
                                </div>
                            </form>
                        </section>

                        <section class="panel">
                            <div class="panel-header">
                                <h2>엑셀 일괄 추가</h2>
                            </div>
                            <p class="section-copy">첫 번째 시트에 사번, 날짜, 유형, 반차구분, 조기퇴근분, 사유 순서로 입력해 주세요. 유형은 휴가, 반차, 유연근무 또는 조기퇴근을 사용할 수 있습니다.</p>
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
                            <p class="section-copy" v-else>휴가, 반차, 조기퇴근 신청을 한 곳에서 승인하거나 반려할 수 있습니다.</p>

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
                                        <td>{{ request.halfDayTypeLabel || (request.earlyLeaveMinutes ? request.earlyLeaveMinutes + '분 조기퇴근' : '-') }}</td>
                                        <td><span :class="statusClass(request.status)">{{ request.statusLabel }}</span></td>
                                        <td>{{ request.reason || '-' }}</td>
                                        <td>{{ request.reviewedByName ? request.reviewedByName + ' (' + request.reviewedByEmployeeCode + ')' : '-' }}</td>
                                        <td>
                                            <div class="button-row" v-if="request.status === 'PENDING'">
                                                <button type="button" class="primary-button small-primary" :disabled="actingId === request.id" @click="reviewRequest(request.id, 'approve')">승인</button>
                                                <button type="button" class="ghost-link" :disabled="actingId === request.id" @click="reviewRequest(request.id, 'reject')">반려</button>
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
