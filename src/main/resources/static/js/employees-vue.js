(function () {
    const { createApp } = Vue;

    const ATTENDANCE_LABELS = {
        WORKING: '출근',
        LATE: '지각',
        ABSENT: '미출근',
        CHECKED_OUT: '퇴근'
    };

    function escapeHtml(value) {
        return String(value ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function workplaceParamValue(workplaceId) {
        return workplaceId === null || workplaceId === undefined || workplaceId === '' ? '' : String(workplaceId);
    }

    function buildQuery(state, pageOverride) {
        const params = new URLSearchParams();
        params.set('page', String(pageOverride ?? state.page));
        if (state.showDeleted) {
            params.set('showDeleted', 'true');
        }
        if (workplaceParamValue(state.workplaceId) !== '') {
            params.set('workplaceId', workplaceParamValue(state.workplaceId));
        }
        return params;
    }

    function buildEmployeesHref(state, extraParams) {
        const params = buildQuery(state);
        Object.entries(extraParams || {}).forEach(([key, value]) => {
            if (value === null || value === undefined || value === '') {
                params.delete(key);
            } else {
                params.set(key, String(value));
            }
        });
        return `/employees?${params.toString()}`;
    }

    function buildStatusClass(stateName) {
        if (stateName === 'WORKING') return 'pill success';
        if (stateName === 'LATE') return 'pill warning';
        if (stateName === 'ABSENT') return 'pill danger';
        return 'pill neutral';
    }

    function parseInitialState() {
        const params = new URLSearchParams(window.location.search);
        return {
            page: Number(params.get('page') || '1'),
            showDeleted: params.get('showDeleted') === 'true',
            workplaceId: params.get('workplaceId') || '',
            editId: params.get('editId') || '',
            createMode: params.get('createMode') === 'true'
        };
    }

    document.addEventListener('DOMContentLoaded', function () {
        const mountNode = document.getElementById('employees-app');
        if (!mountNode || !window.Vue) {
            return;
        }

        createApp({
            data() {
                return {
                    ready: false,
                    loading: false,
                    loadFailed: false,
                    pageError: '',
                    submitting: false,
                    context: null,
                    employees: [],
                    totalCount: 0,
                    totalPages: 1,
                    hasPreviousPage: false,
                    hasNextPage: false,
                    state: parseInitialState(),
                    feedbackMessage: '',
                    feedbackType: 'success',
                    inviteResult: null,
                    inviteCopyMessage: '',
                    inviteCopyError: false,
                    employeeForm: {
                        id: null,
                        employeeCode: '',
                        name: '',
                        role: 'EMPLOYEE',
                        password: '',
                        workStartTime: '',
                        workEndTime: '',
                        workplaceId: ''
                    },
                    employeeFormLoaded: false
                };
            },
            computed: {
                canAccessSqlConsole() {
                    return Boolean(this.context?.canAccessSqlConsole);
                },
                workplaceOptions() {
                    return this.context?.workplaceOptions || [];
                },
                security() {
                    return {
                        csrfName: this.context?.csrfParameterName || '_csrf',
                        csrfToken: this.context?.csrfToken || ''
                    };
                },
                totalCountText() {
                    return `총 ${this.totalCount}명`;
                },
                pageStatusText() {
                    return `${this.state.page} / ${this.totalPages} 페이지`;
                },
                toggleDeletedText() {
                    return this.state.showDeleted ? '활성 직원 보기' : '삭제된 직원 보기';
                },
                createEmployeeHref() {
                    return buildEmployeesHref(this.state, { createMode: true });
                },
                employeeModalOpen() {
                    return this.state.createMode || this.state.editId !== '';
                },
                editing() {
                    return this.state.editId !== '';
                },
                employeeModalTitle() {
                    return this.editing ? '직원 수정' : '직원 등록';
                },
                employeeSubmitLabel() {
                    return this.editing ? '수정 저장' : '직원 등록';
                },
                inviteSummaryText() {
                    if (!this.inviteResult) {
                        return '';
                    }
                    const workplaceName = this.inviteResult.workplaceName || '본사';
                    return `${this.inviteResult.employeeName} (${this.inviteResult.employeeCode}) 직원이 ${workplaceName} 소속으로 가입할 수 있는 링크입니다.`;
                },
                inviteRoleLabel() {
                    if (!this.inviteResult?.role) {
                        return '-';
                    }
                    return this.inviteResult.role === 'ADMIN'
                        ? '회사 관리자'
                        : this.inviteResult.role === 'WORKPLACE_ADMIN'
                            ? '사업장 관리자'
                            : '일반 직원';
                }
            },
            async mounted() {
                try {
                    await this.loadContext();
                    await this.loadEmployees();
                    if (this.employeeModalOpen) {
                        await this.loadEmployeeForm();
                    }
                } catch (error) {
                    this.pageError = error?.message || '직원 목록 화면을 불러오지 못했습니다.';
                    this.loadFailed = true;
                    this.ready = true;
                }
                this.syncBodyModalState();
            },
            updated() {
                this.syncBodyModalState();
            },
            beforeUnmount() {
                document.body.classList.remove('modal-open');
            },
            methods: {
                workplaceParamValue,
                syncBodyModalState() {
                    const open = this.employeeModalOpen || Boolean(this.inviteResult);
                    document.body.classList.toggle('modal-open', open);
                },
                syncLocation() {
                    const params = buildQuery(this.state);
                    if (this.state.editId) {
                        params.set('editId', String(this.state.editId));
                    }
                    if (this.state.createMode) {
                        params.set('createMode', 'true');
                    }
                    window.history.replaceState({}, '', `/app/employees.html?${params.toString()}`);
                },
                async loadContext() {
                    const params = buildQuery(this.state);
                    if (this.state.editId) {
                        params.set('editId', String(this.state.editId));
                    }
                    if (this.state.createMode) {
                        params.set('createMode', 'true');
                    }
                    const response = await fetch(`/employees/page-context?${params.toString()}`, {
                        headers: {
                            'X-Requested-With': 'XMLHttpRequest'
                        }
                    });
                    if (!response.ok) {
                        throw new Error('페이지 컨텍스트 조회 실패');
                    }
                    this.context = await response.json();
                    this.state.page = Number(this.context.currentPage || this.state.page);
                    this.state.showDeleted = Boolean(this.context.showDeleted);
                    this.state.workplaceId = workplaceParamValue(this.context.selectedWorkplaceId);
                    this.state.editId = this.context.editId ? String(this.context.editId) : '';
                    this.state.createMode = Boolean(this.context.createMode);
                    this.ready = true;
                },
                async loadEmployees(nextState) {
                    if (nextState) {
                        this.state.page = Number(nextState.page);
                        this.state.showDeleted = Boolean(nextState.showDeleted);
                        this.state.workplaceId = workplaceParamValue(nextState.workplaceId);
                    }

                    this.loading = true;
                    this.loadFailed = false;

                    try {
                        const response = await fetch(`/employees/list-data?${buildQuery(this.state).toString()}`, {
                            headers: {
                                'X-Requested-With': 'XMLHttpRequest'
                            }
                        });
                        if (!response.ok) {
                            throw new Error('직원 목록 조회 실패');
                        }
                        const data = await response.json();
                        this.employees = data.employees || [];
                        this.totalCount = data.totalCount || 0;
                        this.totalPages = data.totalPages || 1;
                        this.hasPreviousPage = Boolean(data.hasPrevious);
                        this.hasNextPage = Boolean(data.hasNext);
                        this.state.page = Number(data.currentPage || this.state.page);
                        this.syncLocation();
                    } catch (error) {
                        this.loadFailed = true;
                    } finally {
                        this.loading = false;
                    }
                },
                applyFilters() {
                    this.loadEmployees({
                        page: 1,
                        showDeleted: this.state.showDeleted,
                        workplaceId: this.state.workplaceId
                    });
                },
                toggleDeleted() {
                    this.loadEmployees({
                        page: 1,
                        showDeleted: !this.state.showDeleted,
                        workplaceId: this.state.workplaceId
                    });
                },
                goToPage(page) {
                    if (page < 1 || page > this.totalPages || page === this.state.page) {
                        return;
                    }
                    this.loadEmployees({
                        page,
                        showDeleted: this.state.showDeleted,
                        workplaceId: this.state.workplaceId
                    });
                },
                attendanceLabel(stateName) {
                    return ATTENDANCE_LABELS[stateName] || stateName || '-';
                },
                statusClass(stateName) {
                    return buildStatusClass(stateName);
                },
                editEmployeeHref(employeeId) {
                    return buildEmployeesHref(this.state, { editId: employeeId });
                },
                setFeedback(message, type) {
                    this.feedbackMessage = message || '';
                    this.feedbackType = type || 'success';
                },
                clearFeedback() {
                    this.feedbackMessage = '';
                },
                closeInviteResult() {
                    this.inviteResult = null;
                    this.inviteCopyMessage = '';
                    this.inviteCopyError = false;
                },
                resetEmployeeForm() {
                    this.employeeForm = {
                        id: null,
                        employeeCode: '',
                        name: '',
                        role: 'EMPLOYEE',
                        password: '',
                        workStartTime: '',
                        workEndTime: '',
                        workplaceId: this.context?.workplaceScopedAdmin && this.workplaceOptions.length > 0
                            ? String(this.workplaceOptions[0].id)
                            : ''
                    };
                },
                async openCreateModal() {
                    this.state.createMode = true;
                    this.state.editId = '';
                    this.employeeFormLoaded = false;
                    this.syncLocation();
                    await this.loadEmployeeForm();
                },
                async openEditModal(employee) {
                    this.state.editId = String(employee.id);
                    this.state.createMode = false;
                    this.employeeFormLoaded = false;
                    this.syncLocation();
                    await this.loadEmployeeForm();
                },
                closeEmployeeModal() {
                    this.state.editId = '';
                    this.state.createMode = false;
                    this.employeeFormLoaded = false;
                    this.resetEmployeeForm();
                    this.syncLocation();
                },
                async loadEmployeeForm() {
                    try {
                        let response;
                        if (this.state.editId) {
                            response = await fetch(`/employees/${this.state.editId}/form-data`, {
                                headers: { 'X-Requested-With': 'XMLHttpRequest' }
                            });
                        } else {
                            response = await fetch('/employees/create-form-data', {
                                headers: { 'X-Requested-With': 'XMLHttpRequest' }
                            });
                        }
                        if (!response.ok) {
                            throw new Error('직원 폼을 불러오지 못했습니다.');
                        }
                        const form = await response.json();
                        this.employeeForm = {
                            id: form.id ?? null,
                            employeeCode: form.employeeCode ?? '',
                            name: form.name ?? '',
                            role: form.role ?? 'EMPLOYEE',
                            password: '',
                            workStartTime: form.workStartTime ?? '',
                            workEndTime: form.workEndTime ?? '',
                            workplaceId: form.workplaceId === null || form.workplaceId === undefined ? '' : String(form.workplaceId)
                        };
                        this.employeeFormLoaded = true;
                    } catch (error) {
                        this.setFeedback(error.message || '직원 폼을 불러오지 못했습니다.', 'error');
                        this.closeEmployeeModal();
                    }
                },
                async submitEmployeeForm() {
                    this.submitting = true;
                    this.clearFeedback();
                    try {
                        const body = new URLSearchParams();
                        body.set(this.security.csrfName, this.security.csrfToken);
                        body.set('employeeCode', this.employeeForm.employeeCode || '');
                        body.set('name', this.employeeForm.name || '');
                        body.set('role', this.employeeForm.role || 'EMPLOYEE');
                        body.set('password', this.employeeForm.password || '');
                        body.set('workStartTime', this.employeeForm.workStartTime || '');
                        body.set('workEndTime', this.employeeForm.workEndTime || '');
                        if (this.employeeForm.workplaceId !== '') {
                            body.set('workplaceId', this.employeeForm.workplaceId);
                        }

                        const url = this.editing
                            ? `/employees/${this.state.editId}/update-data`
                            : '/employees/create-data';

                        const response = await fetch(url, {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                                'X-Requested-With': 'XMLHttpRequest'
                            },
                            body: body.toString()
                        });
                        const data = await response.json();
                        if (!response.ok || data.success === false) {
                            throw new Error(data.message || '직원 저장에 실패했습니다.');
                        }

                        this.setFeedback(data.message || '저장되었습니다.', 'success');
                        this.closeEmployeeModal();
                        await this.loadEmployees();
                    } catch (error) {
                        this.setFeedback(error.message || '직원 저장에 실패했습니다.', 'error');
                    } finally {
                        this.submitting = false;
                    }
                },
                async postAction(url, params, successMessage) {
                    this.submitting = true;
                    this.clearFeedback();
                    try {
                        const body = new URLSearchParams();
                        body.set(this.security.csrfName, this.security.csrfToken);
                        Object.entries(params || {}).forEach(([key, value]) => {
                            if (value !== null && value !== undefined && value !== '') {
                                body.set(key, String(value));
                            }
                        });

                        const response = await fetch(url, {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                                'X-Requested-With': 'XMLHttpRequest'
                            },
                            body: body.toString()
                        });
                        const data = await response.json();
                        if (!response.ok || data.success === false) {
                            throw new Error(data.message || '요청 처리에 실패했습니다.');
                        }

                        this.setFeedback(data.message || successMessage || '처리가 완료되었습니다.', 'success');
                        this.inviteResult = data.inviteResult || null;
                        this.inviteCopyMessage = '';
                        this.inviteCopyError = false;
                        await this.loadEmployees();
                    } catch (error) {
                        this.setFeedback(error.message || '요청 처리에 실패했습니다.', 'error');
                    } finally {
                        this.submitting = false;
                    }
                },
                inviteEmployee(employee) {
                    this.postAction(`/employees/${employee.id}/invite-link-data`, {}, '직원 초대 링크가 생성되었습니다.');
                },
                resetDevice(employee) {
                    this.postAction(`/employees/${employee.id}/device-reset-data`, {}, '등록된 단말이 초기화되었습니다.');
                },
                toggleUsage(employee) {
                    this.postAction(`/employees/${employee.id}/usage-data`, {
                        active: !employee.active
                    }, employee.active ? '직원이 사용 중지되었습니다.' : '직원이 다시 사용 상태로 변경되었습니다.');
                },
                deleteEmployee(employee) {
                    if (!window.confirm('정말 삭제할까요?')) {
                        return;
                    }
                    this.postAction(`/employees/${employee.id}/delete-data`, {}, '직원이 삭제 목록으로 이동되었습니다.');
                },
                restoreEmployee(employee) {
                    this.postAction(`/employees/${employee.id}/restore-data`, {}, '직원이 복구되었습니다.');
                },
                async copyInviteLink() {
                    if (!this.inviteResult?.inviteUrl) {
                        return;
                    }
                    try {
                        await navigator.clipboard.writeText(this.inviteResult.inviteUrl);
                        this.inviteCopyMessage = '초대 링크를 복사했습니다.';
                        this.inviteCopyError = false;
                    } catch (error) {
                        this.inviteCopyMessage = '브라우저에서 복사를 허용하지 않았습니다. 링크를 직접 선택해 복사해 주세요.';
                        this.inviteCopyError = true;
                    }
                },
                logout() {
                    document.getElementById('logout-form')?.submit();
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
                            <a href="/employees" class="active">직원 목록</a>
                            <a href="/settings/location">설정</a>
                                    <a v-if="canAccessSqlConsole" href="/sql-console">SQL 리포트</a>
                        </nav>
                        <form id="logout-form" action="/logout" method="post">
                            <input type="hidden" :name="security.csrfName" :value="security.csrfToken">
                            <button type="submit" class="secondary-button full-width">로그아웃</button>
                        </form>
                    </aside>

                    <main class="content">
                        <section class="page-header">
                            <div>
                                <div class="eyebrow">Members</div>
                                <h1>직원 목록</h1>
                            </div>
                            <p>직원 등록, 수정, 사용 여부 관리를 한 화면에서 처리할 수 있습니다.</p>
                        </section>

                        <div v-if="feedbackMessage" :class="'alert ' + (feedbackType === 'error' ? 'error' : 'success')">{{ feedbackMessage }}</div>
                        <div v-if="pageError" class="alert error">{{ pageError }}</div>

                        <section class="panel" v-if="ready">
                            <div class="panel-header">
                                <h2>직원 목록</h2>
                                <div class="action-row">
                                    <button type="button" class="primary-button" @click="openCreateModal">직원 등록</button>
                                    <a href="#" class="ghost-link" @click.prevent="toggleDeleted">{{ toggleDeletedText }}</a>
                                </div>
                            </div>
                            <p class="section-copy">각 계정은 한 대의 출퇴근 단말만 등록할 수 있습니다. 다른 기기에서 로그인하려면 여기서 먼저 단말 초기화를 실행해 주세요.</p>
                            <div class="alert error" v-if="loadFailed">직원 목록을 불러오지 못했습니다.</div>
                            <form class="filter-form" @submit.prevent="applyFilters">
                                <input type="hidden" name="page" value="1">
                                <label>
                                    사업장
                                    <select name="workplaceId" v-model="state.workplaceId">
                                        <option value="">전체</option>
                                        <option value="0">본사</option>
                                        <option v-for="workplace in workplaceOptions" :key="workplace.id" :value="String(workplace.id)">{{ workplace.name }}</option>
                                    </select>
                                </label>
                                <button type="submit" class="primary-button">적용</button>
                            </form>
                            <div class="list-summary">
                                <span>{{ totalCountText }}</span>
                                <span>{{ pageStatusText }}</span>
                            </div>
                            <div class="table-scroll">
                                <table class="employees-table compact-table">
                                    <thead>
                                        <tr>
                                            <th>사번</th>
                                            <th>이름</th>
                                            <th>사업장</th>
                                            <th>권한</th>
                                            <th>출근 상태</th>
                                            <th>출근 시간</th>
                                            <th>퇴근 시간</th>
                                            <th>출근 기준</th>
                                            <th>퇴근 기준</th>
                                            <th>관리</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <tr v-if="loading && employees.length === 0">
                                            <td colspan="10" class="empty-state-cell">직원 목록을 불러오는 중입니다.</td>
                                        </tr>
                                        <tr v-else-if="employees.length === 0">
                                            <td colspan="10" class="empty-state-cell">조건에 맞는 직원이 없습니다.</td>
                                                </tr>
                                        <tr v-for="employee in employees" :key="employee.id">
                                            <td>{{ employee.employeeCode }}</td>
                                            <td>{{ employee.name }}</td>
                                            <td class="company-cell">{{ employee.workplaceName }}</td>
                                            <td>{{ employee.role }}</td>
                                            <td class="status-cell">
                                                <span :class="statusClass(employee.attendanceState)">{{ attendanceLabel(employee.attendanceState) }}</span>
                                            </td>
                                            <td class="time-cell emphasis-time">{{ employee.checkInTime }}</td>
                                            <td class="time-cell emphasis-time">{{ employee.checkOutTime }}</td>
                                            <td class="schedule-cell">{{ employee.workStartTime }}</td>
                                            <td class="schedule-cell">{{ employee.workEndTime }}</td>
                                            <td class="manage-cell">
                                                <div class="action-row">
                                                    <button v-if="!employee.deleted" type="button" class="ghost-link small-link" :disabled="submitting" @click="openEditModal(employee)">수정</button>

                                                    <button v-if="!employee.deleted && employee.role !== 'ADMIN'" type="button" class="ghost-link" :disabled="submitting" @click="inviteEmployee(employee)">초대생성</button>

                                                    <button v-if="!employee.deleted && employee.deviceRegistered" type="button" class="ghost-link" :disabled="submitting" @click="resetDevice(employee)">단말 초기화</button>

                                                    <button v-if="!employee.deleted" type="button" :class="employee.active ? 'danger-link' : 'ghost-link'" :disabled="submitting" @click="toggleUsage(employee)">{{ employee.active ? '사용중지' : '사용재개' }}</button>

                                                    <button v-if="!employee.deleted" type="button" class="danger-link" :disabled="submitting" @click="deleteEmployee(employee)">삭제</button>

                                                    <button v-if="employee.deleted" type="button" class="ghost-link" :disabled="submitting" @click="restoreEmployee(employee)">복구</button>
                                                </div>
                                            </td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                            <div class="pagination-bar" v-if="totalPages > 1">
                                <a v-if="hasPreviousPage" href="#" class="ghost-link small-link" @click.prevent="goToPage(state.page - 1)">이전</a>
                                <span class="pagination-current">{{ state.page }} / {{ totalPages }}</span>
                                <a v-if="hasNextPage" href="#" class="ghost-link small-link" @click.prevent="goToPage(state.page + 1)">다음</a>
                            </div>
                        </section>

                        <section class="panel">
                            <div class="panel-header">
                                <h2>엑셀 일괄 등록</h2>
                                <a href="/employees/template" class="ghost-link">샘플 파일 다운로드</a>
                            </div>
                            <p class="section-copy">샘플 \`.xlsx\` 파일의 첫 번째 시트에 \`사번\`, \`이름\`, \`권한\`, \`비밀번호\`, \`사업장\`, \`출근 기준 시간\`, \`퇴근 기준 시간\` 순서로 입력해 주세요. 사업장은 등록된 이름과 정확히 일치해야 하고, 비워두면 본사로 등록됩니다. 개인 시간은 비워둘 수 있고 입력 시 \`HH:mm\` 형식을 사용합니다.</p>
                            <form action="/employees/upload" method="post" enctype="multipart/form-data" class="upload-form">
                                <input type="hidden" :name="security.csrfName" :value="security.csrfToken">
                                <input type="hidden" name="page" :value="state.page">
                                <input type="hidden" name="showDeleted" :value="String(state.showDeleted)">
                                <input type="hidden" name="listWorkplaceId" :value="workplaceParamValue(state.workplaceId)">
                                <label>
                                    엑셀 파일 선택
                                    <input type="file" name="employeeFile" accept=".xlsx">
                                </label>
                                <div class="button-row">
                                    <button type="submit" class="primary-button">엑셀 업로드</button>
                                    <a href="/employees/template" class="ghost-link">샘플 다시 받기</a>
                                </div>
                            </form>
                        </section>

                        <div class="modal-sheet employee-editor-modal" v-if="inviteResult" @click.self="closeInviteResult">
                            <section class="panel employee-editor-panel invite-result-modal">
                                <div class="panel-header">
                                    <h2>직원 초대 링크</h2>
                                    <button type="button" class="ghost-link" @click="closeInviteResult">닫기</button>
                                </div>
                                <p class="invite-result-copy">직원에게 아래 링크만 전달하면 모바일 웹 또는 앱에서 바로 등록을 시작할 수 있습니다. 링크를 보내기 전에 직원 정보와 만료 시각을 한 번 확인해 주세요.</p>
                                <div class="invite-result-highlight">
                                    <div class="invite-highlight-header">
                                        <span class="invite-chip">직원 등록용 링크</span>
                                        <span class="invite-expire-text">만료 시각 {{ inviteResult.expiresAt }}</span>
                                    </div>
                                    <strong>{{ inviteSummaryText }}</strong>
                                </div>
                                <div class="invite-meta-grid">
                                    <article class="invite-meta-card">
                                        <span class="detail-label">직원</span>
                                        <strong>{{ inviteResult.employeeName }}</strong>
                                        <p>초대를 받는 직원 이름입니다.</p>
                                    </article>
                                    <article class="invite-meta-card">
                                        <span class="detail-label">사번</span>
                                        <strong>{{ inviteResult.employeeCode }}</strong>
                                        <p>직원이 이후 로그인에 사용할 사번입니다.</p>
                                    </article>
                                    <article class="invite-meta-card">
                                        <span class="detail-label">권한</span>
                                        <strong>{{ inviteRoleLabel }}</strong>
                                        <p>등록이 끝나면 이 권한으로 계정이 활성화됩니다.</p>
                                    </article>
                                    <article class="invite-meta-card">
                                        <span class="detail-label">사업장</span>
                                        <strong>{{ inviteResult.workplaceName || '본사' }}</strong>
                                        <p>연결될 기본 사업장 정보입니다.</p>
                                    </article>
                                </div>
                                <section class="invite-guide-card">
                                    <span class="detail-label">전달 방법</span>
                                    <ol class="invite-guide-list">
                                        <li>아래 초대 링크 복사 버튼을 눌러 주소를 복사합니다.</li>
                                        <li>직원에게 문자나 메신저로 그대로 전달합니다.</li>
                                        <li>직원은 링크를 열어 모바일 웹 또는 앱에서 등록을 진행합니다.</li>
                                    </ol>
                                </section>
                                <label class="invite-link-field">
                                    <span>직원에게 전달할 초대 링크</span>
                                    <textarea :value="inviteResult.inviteUrl" readonly rows="4"></textarea>
                                    <small>주소가 길어도 전체가 잘 보이도록 여러 줄로 표시합니다. 내용을 수정하지 말고 그대로 보내면 됩니다.</small>
                                </label>
                                <div v-if="inviteCopyMessage" :class="'alert ' + (inviteCopyError ? 'error' : 'success')">{{ inviteCopyMessage }}</div>
                                <div class="button-row">
                                    <button type="button" class="primary-button" @click="copyInviteLink">초대 링크 복사</button>
                                    <button type="button" class="ghost-link" @click="closeInviteResult">닫기</button>
                                </div>
                            </section>
                        </div>

                        <div class="modal-sheet employee-editor-modal" v-if="employeeModalOpen" @click.self="closeEmployeeModal">
                            <section class="panel employee-editor-panel">
                                <div class="panel-header">
                                    <h2>{{ employeeModalTitle }}</h2>
                                    <button type="button" class="ghost-link" @click="closeEmployeeModal">닫기</button>
                                </div>
                                <form class="settings-form" @submit.prevent="submitEmployeeForm">
                                    <div class="form-row" v-if="employeeFormLoaded">
                                        <label>
                                            사번
                                            <input type="text" v-model="employeeForm.employeeCode" placeholder="EMP002">
                                        </label>
                                        <label>
                                            이름
                                            <input type="text" v-model="employeeForm.name" placeholder="직원 이름">
                                        </label>
                                    </div>

                                    <div class="form-row" v-if="employeeFormLoaded">
                                        <label>
                                            권한
                                            <select v-model="employeeForm.role">
                                                <option value="EMPLOYEE">EMPLOYEE</option>
                                                <option v-if="context?.canManageAdminRoles" value="WORKPLACE_ADMIN">WORKPLACE_ADMIN</option>
                                                <option v-if="context?.canManageAdminRoles" value="ADMIN">ADMIN</option>
                                            </select>
                                        </label>
                                        <label>
                                            사업장
                                            <select v-model="employeeForm.workplaceId">
                                                <option v-if="!context?.workplaceScopedAdmin" value="">본사</option>
                                                <option v-for="workplace in workplaceOptions" :key="'modal-' + workplace.id" :value="String(workplace.id)">{{ workplace.name }}</option>
                                            </select>
                                        </label>
                                    </div>

                                    <div class="form-row" v-if="employeeFormLoaded">
                                        <label>
                                            비밀번호
                                            <input type="password" v-model="employeeForm.password" :placeholder="editing ? '변경 시에만 입력' : '직원 계정은 비워둘 수 있음'">
                                            <small class="field-error" v-if="!editing">일반 직원은 비워두면 최초 접속 시 비밀번호를 직접 설정합니다. 관리자 계정은 직접 입력해 주세요.</small>
                                        </label>
                                    </div>

                                    <div class="form-row" v-if="employeeFormLoaded">
                                        <label>
                                            출근 기준 시간
                                            <input type="time" v-model="employeeForm.workStartTime">
                                            <small class="field-error">비워두면 회사 공통 기준 시간을 사용합니다.</small>
                                        </label>
                                        <label>
                                            퇴근 기준 시간
                                            <input type="time" v-model="employeeForm.workEndTime">
                                            <small class="field-error">입력하면 직원별 퇴근 기준 시간으로 저장됩니다.</small>
                                        </label>
                                    </div>

                                    <div class="button-row" v-if="employeeFormLoaded">
                                        <button type="submit" class="primary-button" :disabled="submitting">{{ employeeSubmitLabel }}</button>
                                        <button type="button" class="ghost-link" @click="closeEmployeeModal">취소</button>
                                    </div>
                                </form>
                            </section>
                        </div>
                    </main>
                </div>
            `
        }).mount('#employees-app');
    });
})();
