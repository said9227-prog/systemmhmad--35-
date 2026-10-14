# خطة تحسين وترقية تطبيق "الحكيمي للأدوية والمستلزمات الطبية" لتحمل 500,000 عميل والمعاملات المالية

تهدف هذه الخطة إلى تحويل التطبيق للعمل بكفاءة فائقة وسرعة استجابة عالية مع دعم قاعدة بيانات ضخمة تضم **500,000 عميل**، مئات الآلاف من الفواتير والمدفوعات والمرتجعات وحركات المخزون، باستخدام أحدث تقنيات أندرويد وKotlin (Android Jetpack Paging 3، SQLite B-Tree Indexing، Room Transactions، Atomic Incremental Financial Balances، وStructured Concurrency).

---

## 1. متطلبات المراجعة والمواءمة (User Review Required)

- **استراتيجية تصفح البيانات الضخمة (Pagination First):**
  - منع تحميل القوائم الكاملة (500,000 كائن) في ذاكرة الـ RAM كـ `List<Client>` في Compose أو StateFlow.
  - الاعتماد الحصري على **Android Paging 3** (`PagingSource` و `LazyPagingItems`) مع نوافذ صفحات محددة (Page size: 30-50 عنصر) محملة مباشرة من SQLite.
- **إدارة وحساب أرصدة العملاء (Incremental Balance Maintenance):**
  - الحفاظ على رصيد العميل (`Client.balance`) محدثاً ذرياً (Atomically) داخل معاملات Room (`@Transaction`) عند تسجيل أو تعديل أي فاتورة أو دفعة أو قسط أو مرتجع.
  - استبدال التجميع اليدوي التكراري في الذاكرة بـ SQL Aggregation مباشر (`SELECT SUM(...)`, `SELECT COUNT(...)`).
- **محرك التوليد والاختبار الآلي للبيانات الضخمة (Safe Bulk Scale Engine):**
  - تشغيل عمليات التوليد والضغط في دفعات محصورة (Bounded Batches: 500-1000 سجل لكل دفعة) مع التحكم في استهلاك الذاكرة وإطلاق الموارد لضمان عدم حدوث OutOfMemoryError أو ANR.

---

## 2. البنية المعمارية وتفاصيل النظام (Architecture & System Design)

### 2.1 بنية تخزين وفهرسة قاعدة البيانات (SQLite / Room Optimization)
- **إضافة فهارس مركبة (Composite Indexes):**
  - جدول العملاء `clients`:
    - `Index(value = ["isDeleted", "isPinned", "name"])` لتسريع الترتيب والفلترة الأساسية.
    - `Index(value = ["isDeleted", "phone"])` لتسريع البحث برقم الهاتف.
    - `Index(value = ["isDeleted", "balance"])` لتسريع استعلام كبار المدينين والفلترة حسب المديونية.
  - جدول الفواتير `invoices`:
    - `Index(value = ["clientId", "date", "isDeleted"])` لتسريع كشوفات حسابات العملاء.
    - `Index(value = ["invoiceNumber"])` لتسريع البحث المباشر.
  - جدول المدفوعات `payments`:
    - `Index(value = ["clientId", "date", "isDeleted"])` لتسريع تجميع الحركات المالية للعميل.

### 2.2 بنية التصفح التفاعلي وسرعة العرض (Paging 3 & Jetpack Compose UI)
- **فصل الشاشات عن التدفقات الشاملة (Decoupling In-Memory Full Flows):**
  - تعديل `ClientsScreenData` في `AppViewModel` لمنع تجميع 500,000 عميل مع كافة الفواتير والمدفوعات دفعة واحدة في تدفق واحد (`combine(clients, invoices, ...)`).
  - استخدام `clientsPagedFlow` المدعوم بـ `PagingConfig(pageSize = 40, prefetchDistance = 10, enablePlaceholders = false)`.
  - تطبيق مفاتيح استقرار ثابتة `key = { client.id }` في `items(count = lazyClients.itemCount)` لمنع إعادة رسم العناصر غير الضرورية (Recomposition Overhead).
  - تطبيق بحث فوري ومؤجل (Debounced Search: 300ms) عبر تدفقات Coroutine ملغية للاستعلامات السابقة لتجنب الضغط على وحدة المعالجة أثناء الكتابة.

### 2.3 المعالجة المالية الذرية وحساب الإجماليات (Atomic Accounting & Aggregations)
- **حساب الإجماليات عبر SQL المباشر:**
  - استبدال حلقات `clientsList.sumOf { it.balance }` باستعلامات تجميعية سريعة تنفذها نواة SQLite في مللي ثانية:
    - `SELECT COUNT(*) FROM clients WHERE isDeleted = 0`
    - `SELECT COALESCE(SUM(balance), 0.0) FROM clients WHERE isDeleted = 0 AND balance > 0`
    - `SELECT COALESCE(SUM(balance), 0.0) FROM clients WHERE isDeleted = 0 AND balance < 0`
- **تحديث الرصيد الفوري:**
  - تعديل رصيد العميل بشكل تراكمي عند إنشاء/تعديل/حذف الفواتير أو الدفعات أو المرتجعات، مع الحفاظ على دالة مراجعة شاملة ومطابقة الأرصدة عند الطلب (Balance Reconciliation).

### 2.4 محرك اختبار الضغط والمقاييس الضخمة (Resilient 500k Scale Engine)
- **تحسين `BulkScaleEngine.kt`:**
  - تقسيم إدخال البيانات إلى دفعات متتالية من 500 إلى 1,000 سجل في المعاملة الواحدة (`db.withTransaction { ... }`).
  - تطبيق مراقبة لاستهلاك الذاكرة الحرة في الـ JVM Heap (`Runtime.getRuntime().freeMemory()`) وإدخال فترات انتظار ذكية (Backpressure Throttle) في حال ارتفاع الضغط.
  - إيقاف/إلغاء آمن عبر Coroutine Cancellation دون ترك معاملات معلقة.

---

## 3. التغييرات البرمجية المقترحة (Proposed Changes)

### طبقة البيانات وقاعدة البيانات (Data & Room Layer):
1. **`app/src/main/java/com/example/data/model/Entities.kt`:**
   - تحديث فهارس الكيانات (`Client`, `Invoice`, `Payment`) لتشمل فهارس مركبة مخصصة لاستعلامات التصفح والبحث السريع.
2. **`app/src/main/java/com/example/data/dao/Daos.kt`:**
   - إضافة استعلامات SQL تجميعية للإحصائيات (`getClientTotalsSummary()`, `getDebtorsCount()`, etc.).
   - تحسين استعلامات `searchClientsPagingSource` و `getAllClientsPagingSource`.
   - إضافة استعلامات بحث محددة بدقة مع حدود عليا `LIMIT`.
3. **`app/src/main/java/com/example/data/repository/AppRepository.kt`:**
   - إضافة دوال الحصول على الإحصائيات المجمعة مباشرة من الـ DAO دون تحميل الكيانات.
   - توفير تدفقات PagingSource و Pager مهيأة خصيصاً للبيانات الضخمة.

### طبقة المنطق وإدارة الحالة (ViewModel & Business Logic):
4. **`app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt`:**
   - تعديل تدفق شاشة العملاء `clientsScreenData` ليعتمد على الإحصائيات التجميعية الفورية بدلاً من تحميل 500,000 كائن في الذاكرة.
   - تهيئة تدفق `clientsPagedFlow` مع بحث مدمج وترتيب مسبق على مستوى استعلام SQLite.
5. **`app/src/main/java/com/example/util/BulkScaleEngine.kt`:**
   - دعم التوليد الآمن حتى 500,000 عميل ومعاملة، عبر تشغيل معزول في `Dispatchers.IO` مع معاملات Room المقسمة وإشعار التقدم اللحظي.

### طبقة الواجهة وتجربة المستخدم (Jetpack Compose UI):
6. **`app/src/main/java/com/example/ui/screens/ClientsScreen.kt`:**
   - تحويل القائمة الرئيسية بالكامل لتعمل عبر `lazyClients: LazyPagingItems<Client>`.
   - تعديل بطاقات وعناصر ملخص العملاء (الرصيد الكلي، عدد المدينين، إلخ) لتأخذ قيمها من إحصائيات الـ ViewModel المجمعة.
   - تحسين بطاقة العميل لتقديم عرض فوري وسريع دون عمليات حسابية ثقيلة أثناء التمرير (Fling / Scroll).
7. **`app/src/main/java/com/example/ui/screens/SettingsScreen.kt`:**
   - إضافة خيار اختبار الأداء والضغط حتى 500,000 عميل مع مؤشر مرئي لمعدل استهلاك الذاكرة ومستوى الإنجاز.

---

## 4. خطة التحقق والاختبار (Verification Plan)

### التحقق من البناء والتجميع (Build Verification):
- تشغيل `compile_applet` بعد كل حزمة تعديلات للتأكد من خلو المشروع من أخطاء التجميع والربط وتوافق Room Migrations.

### التحقق من الأداء والاستقرار (Performance & Memory Checks):
- التأكد من أن استهلاك الـ Heap يظل مستقراً وتحت الحدود الآمنة أثناء تصفح والبحث في قائمة العملاء.
- اختبار سلاسة التمرير (60 FPS) وعدم حدوث أي تأخير أو تجمد في واجهة المستخدم (Zero UI Freezes).
- التأكد من دقة وصحة العمليات المحاسبية والأرصدة بالعملات المختلفة (YER, SAR, USD).
