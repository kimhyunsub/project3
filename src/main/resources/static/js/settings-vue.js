(function () {
    const { createApp, nextTick } = Vue;

    const DEFAULT_MAP_CENTER = { lat: 37.5665, lng: 126.9780 };

    function workplaceParamValue(workplaceId) {
        return workplaceId === null || workplaceId === undefined || workplaceId === '' ? '' : String(workplaceId);
    }

    function buildQuery(state) {
        const params = new URLSearchParams();
        if (workplaceParamValue(state.workplaceId) !== '') {
            params.set('workplaceId', workplaceParamValue(state.workplaceId));
        }
        return params;
    }

    function parseInitialState() {
        const params = new URLSearchParams(window.location.search);
        return {
            workplaceId: params.get('workplaceId') || ''
        };
    }

    function defaultCompanyForm() {
        return {
            companyName: '',
            latitude: '',
            longitude: '',
            allowedRadiusMeters: 100,
            lateAfterTime: '09:00',
            noticeMessage: '',
            mobileSkinKey: 'classic',
            enforceSingleDeviceLogin: false
        };
    }

    function defaultWorkplaceForm() {
        return {
            name: '',
            latitude: '',
            longitude: '',
            allowedRadiusMeters: 100,
            noticeMessage: ''
        };
    }

    function fillCompanyForm(source) {
        return {
            companyName: source?.companyName || '',
            latitude: source?.latitude ?? '',
            longitude: source?.longitude ?? '',
            allowedRadiusMeters: source?.allowedRadiusMeters ?? 100,
            lateAfterTime: source?.lateAfterTime || '09:00',
            noticeMessage: source?.noticeMessage || '',
            mobileSkinKey: source?.mobileSkinKey || 'classic',
            enforceSingleDeviceLogin: Boolean(source?.enforceSingleDeviceLogin)
        };
    }

    function fillWorkplaceForm(source) {
        return {
            name: source?.name || '',
            latitude: source?.latitude ?? '',
            longitude: source?.longitude ?? '',
            allowedRadiusMeters: source?.allowedRadiusMeters ?? 100,
            noticeMessage: source?.noticeMessage || ''
        };
    }

    function formDataFromObject(source, security) {
        const formData = new FormData();
        if (security?.csrfName && security?.csrfToken) {
            formData.append(security.csrfName, security.csrfToken);
        }
        Object.entries(source).forEach(([key, value]) => {
            if (typeof value === 'boolean') {
                if (value) {
                    formData.append(key, 'true');
                }
                return;
            }
            formData.append(key, value ?? '');
        });
        return formData;
    }

    document.addEventListener('DOMContentLoaded', function () {
        const mountNode = document.getElementById('settings-app');
        if (!mountNode || !window.Vue) {
            return;
        }

        createApp({
            data() {
                return {
                    ready: false,
                    loading: false,
                    submitting: false,
                    loadFailed: false,
                    context: null,
                    state: parseInitialState(),
                    companyForm: defaultCompanyForm(),
                    workplaceForm: defaultWorkplaceForm(),
                    createWorkplaceForm: defaultWorkplaceForm(),
                    createWorkplaceOpen: false,
                    feedbackMessage: '',
                    feedbackType: 'success',
                    mapRefreshQueued: false
                };
            },
            computed: {
                canAccessSqlConsole() {
                    return Boolean(this.context?.canAccessSqlConsole);
                },
                workplaces() {
                    return this.context?.workplaces || [];
                },
                selectedWorkplace() {
                    return this.context?.selectedWorkplace || null;
                },
                isCompanyTab() {
                    return !this.state.workplaceId;
                },
                isWorkplaceScopedAdmin() {
                    return Boolean(this.context?.workplaceScopedAdmin);
                },
                activeSummaryTitle() {
                    return this.isCompanyTab ? '회사 기본 설정' : '선택한 사업장 설정';
                },
                activeDescription() {
                    if (this.isCompanyTab) {
                        return '사업장을 지정하지 않은 직원은 회사 기본 위치와 반경을 사용합니다.';
                    }
                    return '이 사업장으로 지정된 직원은 아래 위치와 반경을 기준으로 출근 가능 여부가 계산됩니다.';
                },
                companyTabClass() {
                    return ['settings-tab', this.isCompanyTab ? 'active' : ''].join(' ').trim();
                },
                createWorkplaceDisabled() {
                    return this.submitting || this.isWorkplaceScopedAdmin;
                },
                security() {
                    return {
                        csrfName: this.context?.csrfParameterName || '_csrf',
                        csrfToken: this.context?.csrfToken || ''
                    };
                }
            },
            async mounted() {
                this._maps = {};
                await this.loadContext();
                this.syncBodyModalState();
                this.queueMapRefresh();
            },
            updated() {
                this.syncBodyModalState();
                this.queueMapRefresh();
            },
            beforeUnmount() {
                document.body.classList.remove('modal-open');
            },
            methods: {
                queueMapRefresh() {
                    if (this.mapRefreshQueued) {
                        return;
                    }
                    this.mapRefreshQueued = true;
                    nextTick(() => {
                        this.mapRefreshQueued = false;
                        this.refreshMaps();
                    });
                },
                resolveMapCenter(target) {
                    const lat = Number(target?.latitude);
                    const lng = Number(target?.longitude);
                    if (Number.isFinite(lat) && Number.isFinite(lng)) {
                        return { lat, lng };
                    }
                    return DEFAULT_MAP_CENTER;
                },
                bindMap(mapKey, elementId, target, locationName) {
                    if (!window.L) {
                        return;
                    }
                    const element = document.getElementById(elementId);
                    if (!element) {
                        return;
                    }

                    const center = this.resolveMapCenter(target);
                    let entry = this._maps[mapKey];
                    if (!entry) {
                        const map = window.L.map(element).setView([center.lat, center.lng], 16);
                        window.L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                            attribution: '&copy; OpenStreetMap contributors'
                        }).addTo(map);

                        const marker = window.L.marker([center.lat, center.lng]).addTo(map);
                        const circle = window.L.circle([center.lat, center.lng], {
                            radius: Number(target?.allowedRadiusMeters) || 100,
                            color: '#0f766e',
                            fillColor: '#0f766e',
                            fillOpacity: 0.12
                        }).addTo(map);

                        map.on('click', (event) => {
                            target.latitude = Number(event.latlng.lat.toFixed(6));
                            target.longitude = Number(event.latlng.lng.toFixed(6));
                            this.queueMapRefresh();
                        });

                        entry = { map, marker, circle };
                        this._maps[mapKey] = entry;
                    }

                    entry.map.invalidateSize();
                    entry.map.setView([center.lat, center.lng], entry.map.getZoom());
                    entry.marker.setLatLng([center.lat, center.lng]);
                    entry.circle.setLatLng([center.lat, center.lng]);
                    entry.circle.setRadius(Number(target?.allowedRadiusMeters) || 100);
                    if (locationName) {
                        entry.marker.bindPopup(locationName);
                    }
                },
                refreshMaps() {
                    if (!this.ready) {
                        return;
                    }
                    if (this.isCompanyTab && this.context?.companyLocation) {
                        this.bindMap('company', 'company-settings-map', this.companyForm, this.companyForm.companyName || '회사 기본 위치');
                    }
                    if (!this.isCompanyTab && this.selectedWorkplace) {
                        this.bindMap('workplace', 'workplace-settings-map', this.workplaceForm, this.workplaceForm.name || '사업장 위치');
                    }
                    if (this.createWorkplaceOpen) {
                        this.bindMap('create-workplace', 'create-workplace-map', this.createWorkplaceForm, this.createWorkplaceForm.name || '신규 사업장');
                    }
                },
                syncBodyModalState() {
                    document.body.classList.toggle('modal-open', this.createWorkplaceOpen);
                },
                syncLocation() {
                    const query = buildQuery(this.state).toString();
                    const next = query ? `/app/settings.html?${query}` : '/app/settings.html';
                    window.history.replaceState({}, '', next);
                },
                showFeedback(message, type = 'success') {
                    this.feedbackMessage = message;
                    this.feedbackType = type;
                },
                async loadContext(nextWorkplaceId) {
                    if (nextWorkplaceId !== undefined) {
                        this.state.workplaceId = workplaceParamValue(nextWorkplaceId);
                    }

                    this.loading = true;
                    this.loadFailed = false;
                    try {
                        const response = await fetch(`/settings/location/page-context?${buildQuery(this.state).toString()}`, {
                            headers: { 'X-Requested-With': 'XMLHttpRequest' }
                        });
                        if (!response.ok) {
                            throw new Error('설정 정보를 불러오지 못했습니다.');
                        }

                        this.context = await response.json();
                        this.state.workplaceId = workplaceParamValue(this.context.selectedWorkplaceId);
                        this.companyForm = fillCompanyForm(this.context.companyLocation);
                        this.workplaceForm = fillWorkplaceForm(this.context.selectedWorkplace);
                        this.syncLocation();
                        this.ready = true;
                        this.queueMapRefresh();
                    } catch (error) {
                        this.loadFailed = true;
                    } finally {
                        this.loading = false;
                    }
                },
                openCompanyTab() {
                    if (this.isWorkplaceScopedAdmin) {
                        return;
                    }
                    this.showFeedback('');
                    this.loadContext('');
                },
                openWorkplaceTab(workplaceId) {
                    this.showFeedback('');
                    this.loadContext(workplaceId);
                },
                async useCurrentLocation(target) {
                    if (!navigator.geolocation) {
                        this.showFeedback('현재 위치를 사용할 수 없는 브라우저입니다.', 'error');
                        return;
                    }

                    navigator.geolocation.getCurrentPosition(
                        (position) => {
                            target.latitude = Number(position.coords.latitude.toFixed(6));
                            target.longitude = Number(position.coords.longitude.toFixed(6));
                            this.queueMapRefresh();
                        },
                        () => {
                            this.showFeedback('현재 위치를 가져오지 못했습니다.', 'error');
                        },
                        { enableHighAccuracy: true, timeout: 8000 }
                    );
                },
                async submitCompany() {
                    this.submitting = true;
                    try {
                        const response = await fetch('/settings/location/update-data', {
                            method: 'POST',
                            headers: { 'X-Requested-With': 'XMLHttpRequest' },
                            body: formDataFromObject(this.companyForm, this.security)
                        });
                        const result = await response.json();
                        if (!response.ok || !result.success) {
                            throw new Error(result.message || '회사 설정 저장에 실패했습니다.');
                        }
                        this.showFeedback(result.message, 'success');
                        await this.loadContext(this.state.workplaceId);
                    } catch (error) {
                        this.showFeedback(error.message, 'error');
                    } finally {
                        this.submitting = false;
                    }
                },
                async submitWorkplace() {
                    if (!this.selectedWorkplace) {
                        return;
                    }
                    this.submitting = true;
                    try {
                        const response = await fetch(`/settings/location/workplaces/${this.selectedWorkplace.id}/update-data`, {
                            method: 'POST',
                            headers: { 'X-Requested-With': 'XMLHttpRequest' },
                            body: formDataFromObject(this.workplaceForm, this.security)
                        });
                        const result = await response.json();
                        if (!response.ok || !result.success) {
                            throw new Error(result.message || '사업장 설정 저장에 실패했습니다.');
                        }
                        this.showFeedback(result.message, 'success');
                        await this.loadContext(this.selectedWorkplace.id);
                    } catch (error) {
                        this.showFeedback(error.message, 'error');
                    } finally {
                        this.submitting = false;
                    }
                },
                openCreateWorkplaceModal() {
                    this.createWorkplaceForm = defaultWorkplaceForm();
                    this.createWorkplaceOpen = true;
                    this.queueMapRefresh();
                },
                closeCreateWorkplaceModal() {
                    this.createWorkplaceOpen = false;
                },
                async submitCreateWorkplace() {
                    this.submitting = true;
                    try {
                        const response = await fetch('/settings/location/workplaces/create-data', {
                            method: 'POST',
                            headers: { 'X-Requested-With': 'XMLHttpRequest' },
                            body: formDataFromObject(this.createWorkplaceForm, this.security)
                        });
                        const result = await response.json();
                        if (!response.ok || !result.success) {
                            throw new Error(result.message || '사업장 추가에 실패했습니다.');
                        }
                        this.closeCreateWorkplaceModal();
                        this.showFeedback(result.message, 'success');
                        await this.loadContext('');
                    } catch (error) {
                        this.showFeedback(error.message, 'error');
                    } finally {
                        this.submitting = false;
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
                            <a href="/settings/location" class="active">설정</a>
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
                                <div class="eyebrow">Settings</div>
                                <h1>설정</h1>
                            </div>
                            <p>{{ isWorkplaceScopedAdmin ? '담당 사업장 위치와 공지사항을 관리할 수 있습니다.' : '회사 기본 설정과 사업장별 위치 기준을 한 곳에서 관리합니다.' }}</p>
                        </section>

                        <div v-if="feedbackMessage" :class="['alert', feedbackType === 'error' ? 'error' : 'success']">{{ feedbackMessage }}</div>
                        <div v-if="loadFailed" class="alert error">설정 정보를 불러오지 못했습니다.</div>

                        <section class="panel" v-if="ready">
                            <div class="panel-header">
                                <h2>설정 탭</h2>
                                <button v-if="!isWorkplaceScopedAdmin" type="button" class="primary-button small-primary" @click="openCreateWorkplaceModal">사업장 신규 추가</button>
                            </div>
                            <div class="location-tabs settings-tabs">
                                <button v-if="!isWorkplaceScopedAdmin" type="button" :class="companyTabClass" @click="openCompanyTab">회사 기본</button>
                                <button v-for="workplace in workplaces"
                                        :key="workplace.id"
                                        type="button"
                                        :class="['settings-tab', String(workplace.id) === String(state.workplaceId) ? 'active' : '']"
                                        @click="openWorkplaceTab(workplace.id)">
                                    {{ workplace.name }}
                                </button>
                            </div>
                            <p class="section-copy">직원을 특정 사업장으로 지정하면 모바일 출퇴근은 해당 사업장 위치와 반경을 기준으로 동작합니다.</p>
                        </section>

                        <section class="settings-layout" v-if="ready">
                            <article class="panel">
                                <h2>{{ activeSummaryTitle }}</h2>
                                <div class="location-preview" v-if="isCompanyTab">
                                    <p><strong>회사명</strong> <span>{{ context.companyLocation.companyName }}</span></p>
                                    <p><strong>좌표</strong> <span>{{ context.companyLocation.latitude }}, {{ context.companyLocation.longitude }}</span></p>
                                    <p><strong>허용 반경</strong> <span>{{ context.companyLocation.allowedRadiusMeters }}m</span></p>
                                    <p><strong>지각 기준</strong> <span>{{ context.companyLocation.lateAfterTime }}</span></p>
                                    <p><strong>모바일 스킨</strong> <span>{{ context.companyLocation.mobileSkinKey }}</span></p>
                                    <p><strong>단말 제한</strong> <span>{{ context.companyLocation.enforceSingleDeviceLogin ? '단말 1대 제한 사용' : '단말 제한 없음' }}</span></p>
                                </div>
                                <div class="location-preview" v-else-if="selectedWorkplace">
                                    <p><strong>사업장명</strong> <span>{{ selectedWorkplace.name }}</span></p>
                                    <p><strong>좌표</strong> <span>{{ selectedWorkplace.latitude }}, {{ selectedWorkplace.longitude }}</span></p>
                                    <p><strong>허용 반경</strong> <span>{{ selectedWorkplace.allowedRadiusMeters }}m</span></p>
                                    <p><strong>모바일 공지</strong> <span>{{ selectedWorkplace.noticeMessage ? '사업장 공지 사용 중' : '회사 기본 공지 사용' }}</span></p>
                                </div>
                                <div class="map-help-card">
                                    <strong>출근 기준 안내</strong>
                                    <p>{{ activeDescription }}</p>
                                </div>
                            </article>

                            <article class="panel" v-if="isCompanyTab">
                                <h2>회사 기본 설정 수정</h2>
                                <form class="settings-form" @submit.prevent="submitCompany">
                                    <label>
                                        회사명
                                        <input v-model="companyForm.companyName" type="text" @input="queueMapRefresh">
                                    </label>
                                    <div class="map-shell">
                                        <div id="company-settings-map" class="company-map"></div>
                                    </div>
                                    <div class="button-row">
                                        <button type="button" class="ghost-link" @click="useCurrentLocation(companyForm)">현재 위치 사용</button>
                                    </div>
                                    <div class="form-row">
                                        <label>
                                            위도
                                            <input v-model="companyForm.latitude" type="number" step="0.000001" @input="queueMapRefresh">
                                        </label>
                                        <label>
                                            경도
                                            <input v-model="companyForm.longitude" type="number" step="0.000001" @input="queueMapRefresh">
                                        </label>
                                    </div>
                                    <label>
                                        허용 반경(m)
                                        <input v-model="companyForm.allowedRadiusMeters" type="number" @input="queueMapRefresh">
                                    </label>
                                    <label>
                                        지각 기준 시간
                                        <input v-model="companyForm.lateAfterTime" type="text" readonly>
                                    </label>
                                    <label>
                                        모바일 스킨
                                        <select v-model="companyForm.mobileSkinKey">
                                            <option value="classic">Classic Blue</option>
                                            <option value="ocean">Ocean Mint</option>
                                            <option value="sunset">Sunset Coral</option>
                                        </select>
                                    </label>
                                    <label class="toggle-field">
                                        <input v-model="companyForm.enforceSingleDeviceLogin" type="checkbox">
                                        <span>계정당 로그인 단말을 1대로 제한</span>
                                    </label>
                                    <label>
                                        모바일 공지사항
                                        <textarea v-model="companyForm.noticeMessage" rows="6" placeholder="모바일 메인 화면에 노출할 공지사항을 입력해 주세요."></textarea>
                                    </label>
                                    <button type="submit" class="primary-button" :disabled="submitting">저장하기</button>
                                </form>
                            </article>

                            <article class="panel" v-else-if="selectedWorkplace">
                                <h2>사업장 설정 수정</h2>
                                <form class="settings-form" @submit.prevent="submitWorkplace">
                                    <label>
                                        사업장명
                                        <input v-model="workplaceForm.name" type="text" @input="queueMapRefresh">
                                    </label>
                                    <div class="map-shell">
                                        <div id="workplace-settings-map" class="company-map"></div>
                                    </div>
                                    <div class="button-row">
                                        <button type="button" class="ghost-link" @click="useCurrentLocation(workplaceForm)">현재 위치 사용</button>
                                    </div>
                                    <div class="form-row">
                                        <label>
                                            위도
                                            <input v-model="workplaceForm.latitude" type="number" step="0.000001" @input="queueMapRefresh">
                                        </label>
                                        <label>
                                            경도
                                            <input v-model="workplaceForm.longitude" type="number" step="0.000001" @input="queueMapRefresh">
                                        </label>
                                    </div>
                                    <label>
                                        허용 반경(m)
                                        <input v-model="workplaceForm.allowedRadiusMeters" type="number" @input="queueMapRefresh">
                                    </label>
                                    <label>
                                        모바일 공지사항
                                        <textarea v-model="workplaceForm.noticeMessage" rows="6" placeholder="이 사업장 직원에게만 노출할 공지사항을 입력해 주세요."></textarea>
                                    </label>
                                    <button type="submit" class="primary-button" :disabled="submitting">사업장 저장</button>
                                </form>
                            </article>
                        </section>
                    </main>

                    <div class="modal-sheet" v-if="createWorkplaceOpen" @click.self="closeCreateWorkplaceModal">
                        <article class="settings-modal-card">
                            <div class="panel-header">
                                <div>
                                    <div class="eyebrow">New Workplace</div>
                                    <h2>사업장 신규 추가</h2>
                                </div>
                                <button type="button" class="ghost-link" @click="closeCreateWorkplaceModal">닫기</button>
                            </div>
                            <p class="section-copy">새 사업장을 만들면 직원을 해당 사업장 기준 위치로 배정할 수 있습니다.</p>
                            <form class="settings-form" @submit.prevent="submitCreateWorkplace">
                                <label>
                                    사업장명
                                    <input v-model="createWorkplaceForm.name" type="text" @input="queueMapRefresh">
                                </label>
                                <div class="map-shell">
                                    <div id="create-workplace-map" class="company-map"></div>
                                </div>
                                <div class="button-row">
                                    <button type="button" class="ghost-link" @click="useCurrentLocation(createWorkplaceForm)">현재 위치 사용</button>
                                </div>
                                <div class="form-row">
                                    <label>
                                        위도
                                        <input v-model="createWorkplaceForm.latitude" type="number" step="0.000001" @input="queueMapRefresh">
                                    </label>
                                    <label>
                                        경도
                                        <input v-model="createWorkplaceForm.longitude" type="number" step="0.000001" @input="queueMapRefresh">
                                    </label>
                                </div>
                                <label>
                                    허용 반경(m)
                                    <input v-model="createWorkplaceForm.allowedRadiusMeters" type="number" @input="queueMapRefresh">
                                </label>
                                <label>
                                    모바일 공지사항
                                    <textarea v-model="createWorkplaceForm.noticeMessage" rows="5" placeholder="비워두면 회사 기본 공지를 사용합니다."></textarea>
                                </label>
                                <div class="button-row button-row-end">
                                    <button type="button" class="ghost-link" @click="closeCreateWorkplaceModal">취소</button>
                                    <button type="submit" class="primary-button" :disabled="submitting">사업장 추가</button>
                                </div>
                            </form>
                        </article>
                    </div>
                </div>
            `
        }).mount('#settings-app');
    });
})();
