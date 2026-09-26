# PRD: Frontend Layout Evolution (Topbar, Footerbar, and Navigation)

## 1. Problem Statement
The current Hive frontend lacks a consistent global layout structure. Users need a clear way to identify where they are (breadcrumbs), manage their profile (topbar), see system status (footerbar), and navigate between different functional areas like Chat, Prompts, and Agents.

## 2. Target Users
- **End Users**: Need a simple and intuitive interface to interact with AI via Chat.
- **AI Developers/Admins**: Need quick access to configuration areas (Prompts, Agents) and system information.

## 3. Success Metrics
- **Navigation Efficiency**: Users can switch between Chat, Prompts, and Agents in less than 2 clicks.
- **Context Awareness**: Users can always identify their current location via breadcrumbs.
- **System Transparency**: System version and status are always visible in the footer.

## 4. Functional Requirements

### 4.1 Topbar (Global Header)
| ID | Requirement | Description | Priority |
|----|-------------|-------------|----------|
| FR-01 | Branding | Display Hive logo and application name. Clicking it returns to the home/dashboard. | Must Have |
| FR-02 | User Profile | Display user avatar and name. Clicking opens a menu with "Settings" and "Logout". | Must Have |
| FR-03 | Breadcrumbs | Dynamic navigation path showing the current location (e.g., Home > Chat > Project X). | Should Have |
| FR-04 | Sticky Position | Topbar must remain fixed at the top of the viewport. | Must Have |

### 4.2 Footerbar (Global Footer)
| ID | Requirement | Description | Priority |
|----|-------------|-------------|----------|
| FR-05 | System Info | Display current application version and environment (e.g., v1.0.0 - Production). | Must Have |
| FR-06 | API Status | Visual indicator of backend connectivity (Online/Offline). | Could Have |
| FR-07 | Quick Links | Links to Documentation, Support, and Privacy Policy. | Should Have |
| FR-08 | Copyright | Standard copyright notice (e.g., © 2025 Hive AI). | Must Have |

### 4.3 Navigation Strategy
| ID | Requirement | Description | Priority |
|----|-------------|-------------|----------|
| FR-09 | Main Navigation | Sidebar or Menu to switch between 'Chat', 'Prompts', and 'Agents'. | Must Have |
| FR-10 | Active State | Highlight the current active section in the navigation menu. | Must Have |
| FR-11 | Responsive Menu | Navigation should collapse into a hamburger menu on mobile devices. | Must Have |

## 5. User Stories

### US-01: Global Navigation
**As a** user,
**I want** a consistent way to navigate between Chat, Prompts, and Agents,
**so that** I can easily switch contexts without losing my place.

**Acceptance Criteria:**
1. Given I am on any page, when I look at the navigation menu, then I see links for Chat, Prompts, and Agents.
2. Given I click on "Prompts", when the page loads, then the "Prompts" item is highlighted as active.
3. Given I am on a mobile device, when I click the hamburger icon, then the navigation menu appears.

### US-02: User Profile Management
**As a** user,
**I want** to see my profile information and have access to settings,
**so that** I can manage my account and log out securely.

**Acceptance Criteria:**
1. Given I am logged in, when I look at the Topbar, then I see my avatar and name.
2. Given I click on my profile, when the dropdown opens, then I see "Settings" and "Logout" options.
3. Given I click "Logout", when the action is confirmed, then I am redirected to the Login page.

### US-03: Contextual Awareness (Breadcrumbs)
**As a** user,
**I want** to see my current path in the application,
**so that** I understand the hierarchy of the page I am viewing.

**Acceptance Criteria:**
1. Given I am in a specific chat, when I look at the Topbar, then I see "Home > Chat > [Chat Name]".
2. Given I click on "Chat" in the breadcrumb, when the action is performed, then I am taken back to the Chat list/dashboard.

## 6. Prioritization (RICE Scoring)

| Requirement | Reach | Impact | Confidence | Effort | RICE Score | Priority |
|-------------|-------|--------|------------|--------|------------|----------|
| Main Navigation (FR-09) | 100% | 3 (Massive) | 100% | 2 | 150 | Must Have |
| User Profile (FR-02) | 100% | 2 (High) | 100% | 1 | 200 | Must Have |
| Branding (FR-01) | 100% | 1 (Medium) | 100% | 0.5 | 200 | Must Have |
| System Info (FR-05) | 100% | 0.5 (Low) | 100% | 0.5 | 100 | Must Have |
| Breadcrumbs (FR-03) | 80% | 1 (Medium) | 80% | 1.5 | 42.6 | Should Have |
| Quick Links (FR-07) | 50% | 0.5 (Low) | 80% | 0.5 | 40 | Should Have |
| API Status (FR-06) | 100% | 1 (Medium) | 50% | 1 | 50 | Could Have |

## 7. Mandatory Testing Strategy

### 7.1 Unit Tests (Vitest)
- **Breadcrumb Logic**: Test the utility function that generates breadcrumbs from the current route.
- **User Store**: Test Pinia store actions for fetching user profile and logging out.
- **Navigation Logic**: Test the logic that determines the active state of menu items.

### 7.2 Component Tests (Vue Test Utils)
- **Topbar.vue**:
    - Verify branding is rendered.
    - Verify user profile dropdown opens on click.
    - Verify breadcrumbs display correct labels based on props/route.
- **Footerbar.vue**:
    - Verify version and environment are displayed correctly.
    - Verify links have correct `href` attributes.
- **AppNavigation.vue**:
    - Verify all main links (Chat, Prompts, Agents) are present.
    - Verify active class is applied to the correct link.
    - Verify mobile menu toggle functionality.

### 7.3 Integration Tests
- Verify that clicking "Logout" in the Topbar correctly clears the session and redirects to `/login`.
- Verify that navigating between sections updates the breadcrumbs and active state correctly.

## 9. User Journey Map

| Stage | Action | Touchpoint | Pain Point | Opportunity |
|-------|--------|------------|------------|-------------|
| **Discovery** | User logs in and lands on Chat | Login Page -> Chat View | Lack of context on where they are | Show breadcrumbs immediately |
| **Navigation** | User wants to manage Prompts | Navigation Menu | Hard to find other sections | Clear sidebar/menu with 'Prompts' link |
| **Context Switch** | User switches from Chat to Agents | Navigation Menu -> Agents View | Losing track of current chat | Highlight 'Agents' as active in menu |
| **System Check** | User wants to check app version | Footerbar | Version hidden in settings | Display version clearly in the footer |
| **Account Mgmt** | User wants to log out | Topbar -> Profile Dropdown | Logout button buried | Quick access via profile avatar |

## 10. Constraints & Open Questions
- **Constraint**: Must use PrimeVue components (e.g., `Menubar`, `Breadcrumb`, `Avatar`).
- **Constraint**: Must follow the "Deep Indigo & Amber" color scheme defined in ISSUE_008.
- **Open Question**: Should the sidebar be persistent or collapsible? (Recommendation: Collapsible for better focus on Chat).
- **Open Question**: Where will the "Prompts" and "Agents" views be located? (Assumption: `/prompts` and `/agents`).
