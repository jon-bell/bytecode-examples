#include <stdio.h>
#include <stdlib.h>
#include <stddef.h>
#include <string.h>
#include "jvmti.h"
#include "jni.h"
#pragma GCC diagnostic ignored "-Wwrite-strings"
#pragma GCC diagnostic ignored "-Wmissing-declarations"

typedef struct {
	/* JVMTI Environment */
	jvmtiEnv *jvmti;
	JNIEnv * jni;
	jboolean vm_is_started;
	jboolean vmDead;

	/* Data access Lock */
	jrawMonitorID lock;
	JavaVM* jvm;
} GlobalAgentData;

struct Field;
/**
 * We will need to cache information about each java class to be able to reference back to their fields later
 */
typedef struct Clazz {
	char *name;
	jclass clazz;
	Field **fields;
	int nFields;
	bool fieldsCalculated;
	Clazz * super;
	Clazz ** intfcs;
	int nIntfc;
	int fieldOffsetFromParent;
	int fieldOffsetFromInterfaces;
	bool enqueued;
} Clazz;
typedef struct Field {
	Clazz *clazz;
	char *name;
	jfieldID fieldID;
};
typedef struct FieldList {
	Field * car;
	struct FieldList * cdr;
} FieldList;
struct Tag;
typedef struct PointedToList {
	Tag * car;
	struct PointedToList * cdr;
};
/**
 * The datastructure that we will associate each object with
 */
typedef struct Tag {
	FieldList * directFieldList;
	PointedToList * pointedTo;
	Clazz * clazz;
	bool visited;
};
typedef struct ClassList {
	Clazz * car;
	struct ClassList *cdr;
} ClassList;
static Clazz ** classCache;

static int sizeOfClassCache;
static GlobalAgentData *gdata;

void fatal_error(const char * format, ...) {
	va_list ap;

	va_start(ap, format);
	(void) vfprintf(stderr, format, ap);
	(void) fflush(stderr);
	va_end(ap);
	exit(3);
}

static void check_jvmti_error(jvmtiEnv *jvmti, jvmtiError errnum,
		const char *str) {
	if (errnum != JVMTI_ERROR_NONE) {
		char *errnum_str;

		errnum_str = NULL;
		(void) jvmti->GetErrorName(errnum, &errnum_str);

		printf("ERROR: JVMTI: %d(%s): %s\n", errnum,
				(errnum_str == NULL ? "Unknown" : errnum_str),
				(str == NULL ? "" : str));
	}
}
/* Enter a critical section by doing a JVMTI Raw Monitor Enter */
static void enter_critical_section(jvmtiEnv *jvmti) {
	jvmtiError error;

	error = jvmti->RawMonitorEnter(gdata->lock);
	check_jvmti_error(jvmti, error, "Cannot enter with raw monitor");
}

/* Exit a critical section by doing a JVMTI Raw Monitor Exit */
static void exit_critical_section(jvmtiEnv *jvmti) {
	jvmtiError error;

	error = jvmti->RawMonitorExit(gdata->lock);
	check_jvmti_error(jvmti, error, "Cannot exit with raw monitor");
}
/**
 * Compute the offset of a class' fields from its super class,
 * following the spec here: http://docs.oracle.com/javase/7/docs/platform/jvmti/jvmti.html#jvmtiHeapReferenceInfoField
 */
static int computeFieldOffsetsFromParent(Clazz * c) {
	if (!c->super) {
		if (c->fieldOffsetFromParent < 0)
			c->fieldOffsetFromParent = 0;
		return c->fieldOffsetFromParent;
	}
	if (c->fieldOffsetFromParent < 0) {
		c->fieldOffsetFromParent = computeFieldOffsetsFromParent(c->super);
		return c->fieldOffsetFromParent + c->nFields;
	}
	return c->fieldOffsetFromParent + c->nFields;
}
/**
 * Collect all of the interfaces that a class implements (including super interfaces, parent classes, etc)
 * Needed to calculate field offsets.
 */
static void collectAllInterfaces(Clazz *c, ClassList *lst) {
	int i;
	for (i = 0; i < c->nIntfc; i++) {
		if (c->intfcs[i] && !c->intfcs[i]->enqueued) {
			c->intfcs[i]->enqueued = true;
			lst->cdr = new ClassList();
			lst->cdr->car = c->intfcs[i];
			lst->cdr->cdr = NULL;
			collectAllInterfaces(c->intfcs[i], lst);
		}
	}
	if (c->super)
		collectAllInterfaces(c->super, lst);
}
/**
 * Compute the offset of a class' fields from its parent interfaces,
 * following the spec here: http://docs.oracle.com/javase/7/docs/platform/jvmti/jvmti.html#jvmtiHeapReferenceInfoField
 */
static void computeFieldOffsetsFromInterfaces(Clazz * c) {
	if (c->fieldOffsetFromInterfaces < 0) {
		//Collect all interfaces
		ClassList lst;
		lst.cdr = NULL;
		collectAllInterfaces(c, &lst);
		ClassList * p;
		p = lst.cdr;
		c->fieldOffsetFromInterfaces = 0;
		while (p && p->car) {
			c->fieldOffsetFromInterfaces += p->car->nFields;
			p->car->enqueued = false;
			p = p->cdr;
		}
	}
}
/**
 * Given a field index (as returned as a jvmtiHeapReferenceInfoField), find the actual field that it represents
 * See http://docs.oracle.com/javase/7/docs/platform/jvmti/jvmti.html#jvmtiHeapReferenceInfoField
 */
static Field* getField(Clazz * c, int rawIdx) {
	if (c->fieldOffsetFromInterfaces < 0)
		computeFieldOffsetsFromParent(c);
	if (c->fieldOffsetFromInterfaces < 0)
		computeFieldOffsetsFromInterfaces(c);
	int idx = rawIdx - c->fieldOffsetFromInterfaces;
	if (idx >= c->fieldOffsetFromParent)
		idx = idx - c->fieldOffsetFromParent;
	else
		return getField(c->super, rawIdx);
	if (idx < 0 || idx >= c->nFields)
		return NULL;
	return c->fields[idx];
}
/**
 * Free the tag structure that we associate with objects.
 * Typically will just delete pointer information, unless freeFully is specified, in which case
 * the tag will be freed completely.
 */
static void freeTag(Tag *tag, bool freeFully) {
	if (tag) {
		tag->visited = 0;
		if (tag->clazz && freeFully) {
			Clazz * c = tag->clazz;
			if (c->name)
				gdata->jvmti->Deallocate((unsigned char*) (void*) c->name);
			if (c->fields) {
				int i;
				for (i = 0; i < c->nFields; i++) {
					if (c->fields[i]->name) {
						gdata->jvmti->Deallocate(
								(unsigned char*) (void*) c->fields[i]->name);
					}
					delete (c->fields[i]);
				}
				delete (c->fields);
			}
			if (c->intfcs)
				delete (c->intfcs);
		}
		if (tag->directFieldList) {
			FieldList *t;
			while (tag->directFieldList) {
				t = tag->directFieldList->cdr;
				free(tag->directFieldList);
				tag->directFieldList = t;
			}
		}
		if (tag->pointedTo) {
			PointedToList *t;
			while (tag->pointedTo) {
				t = tag->pointedTo->cdr;
				free(tag->pointedTo);
				tag->pointedTo = t;
			}
		}
		if (freeFully)
			free(tag);
	}
}
/**
 * Callback for when an object is freed - we need to delete our tag on the object too.
 * Note that because our tag is just malloc'ed (and not a java object), we can trivially free
 * it directly within this callback.
 */
static void JNICALL
cbObjectFree(jvmtiEnv *jvmti_env, jlong tag) {
	if (gdata->vmDead) {
		return;
	}
	jvmtiError error;
	if (tag) {
		freeTag((Tag*) tag, true);
	}
}
/**
 * Update our cache of all of the loaded classes and their fields. We will need this information
 * to understand field relationships, which require calculating super classes, super interfaces, etc.
 */
static void updateClassCache(JNIEnv *env) {
	jvmtiError err;
	jint nClasses;
	jclass* classes;

	err = gdata->jvmti->GetLoadedClasses(&nClasses, &classes);
	check_jvmti_error(gdata->jvmti, err, "Cannot get classes");
	if (classCache != NULL) {
		free(classCache);
	}
	classCache = new Clazz*[nClasses];
	memset(classCache, 0, sizeof(Clazz*) * nClasses);
	sizeOfClassCache = nClasses;

	Clazz *c;
	int i;
	jlong tag;
	jint status;
	Tag *t;
	for (i = 0; i < nClasses; i++) {
		err = gdata->jvmti->GetTag(classes[i], &tag);
		check_jvmti_error(gdata->jvmti, err, "Unable to get class tag");
		if (tag) //We already built this class info object - and the class info object is in the tag of the class object
		{
			classCache[i] = ((Tag*) tag)->clazz;
			continue;
		}
		err = gdata->jvmti->GetClassStatus(classes[i], &status);
		check_jvmti_error(gdata->jvmti, err, "Cannot get class status");
		if ((status & JVMTI_CLASS_STATUS_PREPARED) == 0) {
			classCache[i] = NULL;
			continue;
		}
		c = new Clazz();
		t = new Tag();
		t->visited = false;
		t->clazz = c;
		t->directFieldList = NULL;
		t->pointedTo = NULL;
		c->fieldOffsetFromParent = -1;
		c->fieldOffsetFromInterfaces = -1;
		c->fieldsCalculated = false;
		c->clazz = (jclass) env->NewGlobalRef(classes[i]);
		gdata->jvmti->GetClassSignature(classes[i], &c->name, NULL);
		classCache[i] = c;
		err = gdata->jvmti->SetTag(classes[i], (ptrdiff_t) (void*) t);
		check_jvmti_error(gdata->jvmti, err, "Cannot set class tag");
	}
	jclass *intfcs;
	jfieldID *fields;
	int j;
	jclass super;
	//Now that we've built info on each class, we will make sure that for each one,
	//we also have pointers to its super class, interfaces, etc.
	for (i = 0; i < nClasses; i++) {
		if (classCache[i] && !classCache[i]->fieldsCalculated) {
			c = classCache[i];

			super = env->GetSuperclass(classes[i]);
			if (super) {
				err = gdata->jvmti->GetTag(super, &tag);
				check_jvmti_error(gdata->jvmti, err, "Cannot get super class");
				if (tag)
					c->super = ((Tag*) (ptrdiff_t) tag)->clazz;
			}

			//Get the fields
			err = gdata->jvmti->GetClassFields(c->clazz, &(c->nFields),
					&fields);
			check_jvmti_error(gdata->jvmti, err, "Cannot get class fields");
			c->fields = new Field*[c->nFields];
			for (j = 0; j < c->nFields; j++) {
				c->fields[j] = new Field();
				c->fields[j]->clazz = c;
				c->fields[j]->fieldID = fields[j];
				err = gdata->jvmti->GetFieldName(c->clazz, fields[j],
						&(c->fields[j]->name), NULL, NULL);
				check_jvmti_error(gdata->jvmti, err, "Can't get field name");

			}
			gdata->jvmti->Deallocate((unsigned char *) (void*) fields);

			//Get the interfaces
			err = gdata->jvmti->GetImplementedInterfaces(classes[i],
					&(c->nIntfc), &intfcs);
			check_jvmti_error(gdata->jvmti, err, "Cannot get interface info");
			c->intfcs = new Clazz*[c->nIntfc];
			for (j = 0; j < c->nIntfc; j++) {
				err = gdata->jvmti->GetTag(intfcs[j], &tag);
				check_jvmti_error(gdata->jvmti, err,
						"Cannot get interface info");
				if (tag) {
					c->intfcs[j] = ((Tag*) tag)->clazz;
				} else
					c->intfcs[j] = NULL;
			}
			gdata->jvmti->Deallocate((unsigned char*) (void*) intfcs);
			classCache[i]->fieldsCalculated = 1;
		}
	}
}
/**
 * Callback for heap reference following. Propogates points-to through tags.
 * For objects that are directly pointed to by a static field, it will also
 * note which static fields point to them.
 */
JNIEXPORT static int cbHeapReference(jvmtiHeapReferenceKind reference_kind,
		const jvmtiHeapReferenceInfo* reference_info, jlong class_tag,
		jlong referrer_class_tag, jlong size, jlong* tag_ptr,
		jlong* referrer_tag_ptr, jint length, void* user_data) {
	jvmtiError err;
	if (reference_kind == JVMTI_HEAP_REFERENCE_CONSTANT_POOL)
		return 0;
	if (reference_kind != JVMTI_HEAP_REFERENCE_FIELD
			&& reference_kind != JVMTI_HEAP_REFERENCE_ARRAY_ELEMENT
			&& reference_kind != JVMTI_HEAP_REFERENCE_STATIC_FIELD) {
		//We won't bother propogating pointers along other kinds of references
		//(e.g. from a class to its classloader - see http://docs.oracle.com/javase/7/docs/platform/jvmti/jvmti.html#jvmtiHeapReferenceKind )
		return JVMTI_VISIT_OBJECTS;
	}

	if (reference_kind == JVMTI_HEAP_REFERENCE_STATIC_FIELD) {
		//We are visiting a static field directly.
		if (referrer_tag_ptr > 0) {
			Clazz* c = ((Tag*) (*referrer_tag_ptr))->clazz;
			Field* f = getField(c, reference_info->field.index);
			if (f) {
				if (*tag_ptr == 0) {
					FieldList * fl = new FieldList();
					fl->car = f;
					fl->cdr = NULL;
					Tag * t = new Tag();
					t->visited = false;
					t->clazz = NULL;
					t->directFieldList = fl;
					t->pointedTo = NULL;
					*tag_ptr = (ptrdiff_t) (void*) t;
				}
				if (*tag_ptr) {
					//Something is already pointing to this object
					FieldList * fl = ((Tag*) *tag_ptr)->directFieldList;
					if (!fl) {
						fl = new FieldList();
						fl->car = f;
						fl->cdr = NULL;
						((Tag*) *tag_ptr)->directFieldList = fl;
						return JVMTI_VISIT_OBJECTS;
					}
					FieldList *p = fl;

					while (fl && fl->car) {
						if (fl->car == f) {
							return JVMTI_VISIT_OBJECTS;
						}
						if (!fl->cdr)
							break;
						fl = fl->cdr;
					}

					fl->cdr = new FieldList();
					fl->cdr->car = f;
					fl->cdr->cdr = NULL;
				}
			}
		}
	}

	//Not directly pointed to by an SF.
	if (*referrer_tag_ptr) {
		if (!*tag_ptr) {
			Tag * t = new Tag();
			t->visited = false;
			t->clazz = NULL;
			t->directFieldList = NULL;
			t->pointedTo = NULL;
			*tag_ptr = (ptrdiff_t) (void*) t;
		}
		PointedToList * t = new PointedToList();
		t->car = (Tag*) *referrer_tag_ptr;
		t->cdr = ((Tag*) *tag_ptr)->pointedTo;
		((Tag*) *tag_ptr)->pointedTo = t;
	}

	return JVMTI_VISIT_OBJECTS;
}
/**
 * Append all fields in the "toAppendList" to the "appendTo" list, making
 * sure not to add duplicates
 */
static void appendFields(FieldList * toAppend, FieldList ** appendTo) {
	FieldList * p;
	bool found;
	while (toAppend && toAppend->car) {
		p = *appendTo;
		found = false;
		if (!p) {
			*appendTo = new FieldList();
			(*appendTo)->car = toAppend->car;
			(*appendTo)->cdr = NULL;
			found = true;
		} else
			while (p && p->car) {
				if (p->car == toAppend->car) {
					found = true;
					break;
				}
				if (p->cdr == NULL)
					break;
				p = p->cdr;
			}
		if (!found) {
			p->cdr = new FieldList();
			p->cdr->car = toAppend->car;
			p->cdr->cdr = NULL;
		}
		toAppend = toAppend->cdr;
	}
}
/**
 * For a given object represented by tag t, make its list of static field
 * roots complete, by recursively appending all of the static fields that
 * point to things that point to this.
 */
static FieldList* visitPointsTo(Tag * t) {
	if (t->visited)
		return t->directFieldList;
	PointedToList * p = t->pointedTo;
	t->visited = true;
	while (p && p->car) {
		appendFields(visitPointsTo(p->car), &t->directFieldList);
		p = p->cdr;
	}
	return t->directFieldList;
}
/*
 * Implementation of _getObjRoots JNI function.
 * Uses visitPointsTo, then simply builds a java array of the result to return.
 */
JNIEXPORT static jobjectArray JNICALL getObjRoots(JNIEnv *env, jclass klass, jobject o) {
	if (gdata->vmDead) {
		return NULL;
	}
	if (!o) {
		return NULL;
	}
	jvmtiError error;
	jlong tag;

	error = gdata->jvmti->GetTag(o, &tag);
	check_jvmti_error(gdata->jvmti, error, "Cannot get object tag");
	visitPointsTo((Tag *) tag);

	FieldList * fl = ((Tag *) tag)->directFieldList;
	int nObjs = 0;
	FieldList * t = fl;
	while (t && t->car) {
		nObjs++;
		t = t->cdr;
	}
	jclass fieldClass = env->FindClass("java/lang/reflect/Field");
	if(fieldClass == NULL)
		fatal_error("Unable to find 'field' class!");
	jobjectArray ret = (jobjectArray) env->NewObjectArray(nObjs,
			fieldClass, NULL);
	jobject _o;
	int i = 0;
	while (fl && fl->car) {
		_o = env->ToReflectedField(fl->car->clazz->clazz,fl->car->fieldID,true);
//				env->NewObject(gdata->staticFieldClass,
//				gdata->staticFieldConstructor, fl->car->clazz->clazz,
//				env->NewStringUTF(fl->car->name));
		env->SetObjectArrayElement(ret, i, _o);
		fl = fl->cdr;
		i++;
	}
	return ret;
}
/**
 * Callback that we use to make sure that points-to data gets cleared up before
 * we begin building it again. Needed so that we can invoke crawlHeap() multiple times
 * in different JVM states and get different results :)
 */
jvmtiIterationControl cbHeapCleanup(jvmtiObjectReferenceKind reference_kind,
		jlong class_tag, jlong size, jlong* tag_ptr, jlong referrer_tag,
		jint referrer_index, void* user_data) {
	if (*tag_ptr) {
		Tag *t = (Tag*) *tag_ptr;
		freeTag(t, false);
	}
	return JVMTI_ITERATION_CONTINUE;
}
/**
 * Implementation of JNI _crawlHeap function. First, it makes sure that we know about
 * all of the currently loaded classes.
 * Then, it clears any existing points-to data cached.
 * Finally, it will follow references through the heap and build a reverse points-to graph
 */
JNIEXPORT static void JNICALL crawlHeap(JNIEnv *env, jclass klass) {
	if (gdata->vmDead) {
		return;
	}
	updateClassCache(env);
	gdata->jvmti->IterateOverReachableObjects(NULL, NULL, &cbHeapCleanup,
			NULL);

	jvmtiHeapCallbacks * cb = new jvmtiHeapCallbacks();
	memset(cb, 0, sizeof(jvmtiHeapCallbacks));
	cb->heap_reference_callback = &cbHeapReference;
	gdata->jvmti->FollowReferences(0,NULL, NULL, cb, NULL);
}

/*
 * Callback we receive when the JVM terminates - no more functions can be called after this
 */
static void JNICALL callbackVMDeath(jvmtiEnv * jvmti_env, JNIEnv * jni_env)
{
	gdata->vmDead = JNI_TRUE;
}

/*
 * Callback we get when the JVM starts up, but before its initialized.
 * Sets up the JNI calls.
 */
static void JNICALL cbVMStart(jvmtiEnv * jvmti, JNIEnv * env)
{

	enter_critical_section (jvmti);
	{
		jclass klass;
		jfieldID field;
		jint rc;
		static JNINativeMethod registry[2] =
		{	{	"_crawl", "()V", (void*) &crawlHeap},
			{	"_getRoots",
				"(Ljava/lang/Object;)[Ljava/lang/reflect/Field;",
				(void*) &getObjRoots}};
		/* Register Natives for class whose methods we use */
		klass = env->FindClass(
				"net/jonbell/examples/jvmti/walking/runtime/HeapWalker");
		if (klass == NULL) {
			fatal_error(
					"ERROR: JNI: Cannot find JNI Helper with FindClass\n");
		}

		rc = env->RegisterNatives(klass, registry, 2);
		if (rc != 0) {
			fatal_error("ERROR: JNI: Cannot register natives\n");
		}
		/* Engage calls. */
		field = env->GetStaticFieldID(klass, "engaged", "I");
		if (field == NULL) {
			fatal_error("ERROR: JNI: Cannot get field\n");
		}
		env->SetStaticIntField(klass, field, 1);

		gdata->vm_is_started = JNI_TRUE;

	}
	exit_critical_section(jvmti);
}

/*
 * Callback that is notified when our agent is loaded. Registers for event
 * notifications.
 */
JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options,
		void *reserved) {
	static GlobalAgentData data;
	jvmtiError error;
	jint res;
	jvmtiEventCallbacks callbacks;
	jvmtiEnv *jvmti = NULL;
	jvmtiCapabilities capa;

	(void) memset((void*) &data, 0, sizeof(data));
	gdata = &data;
//save jvmti for later
	gdata->jvm = jvm;
	res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_0);
	gdata->jvmti = jvmti;

	if (res != JNI_OK || jvmti == NULL) {
		/* This means that the VM was unable to obtain this version of the
		 *   JVMTI interface, this is a fatal error.
		 */
		printf("ERROR: Unable to access JVMTI Version 1 (0x%x),"
				" is your J2SE a 1.5 or newer version?"
				" JNIEnv's GetEnv() returned %d\n", JVMTI_VERSION_1, res);

	}

//Register our capabilities
	(void) memset(&capa, 0, sizeof(jvmtiCapabilities));
	capa.can_tag_objects = 1;
	capa.can_generate_object_free_events = 1;

	error = jvmti->AddCapabilities(&capa);
	check_jvmti_error(jvmti, error,
			"Unable to get necessary JVMTI capabilities.");

//Register callbacks
	(void) memset(&callbacks, 0, sizeof(callbacks));
	callbacks.VMDeath = &callbackVMDeath;
	callbacks.VMStart = &cbVMStart;
	callbacks.ObjectFree = &cbObjectFree;

	error = jvmti->SetEventCallbacks(&callbacks, (jint) sizeof(callbacks));
	check_jvmti_error(jvmti, error, "Cannot set jvmti callbacks");

//Register for events
	error = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_START,
			(jthread) NULL);
	check_jvmti_error(jvmti, error, "Cannot set event notification");
	check_jvmti_error(jvmti, error, "Cannot set event notification");
	error = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT,
			(jthread) NULL);
	check_jvmti_error(jvmti, error, "Cannot set event notification");
	error = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_DEATH,
			(jthread) NULL);
	check_jvmti_error(jvmti, error, "Cannot set event notification");
	error = jvmti->SetEventNotificationMode(JVMTI_ENABLE,
			JVMTI_EVENT_OBJECT_FREE, (jthread) NULL);
	check_jvmti_error(jvmti, error, "Cannot set event notification");

//Set up a few locks
	error = jvmti->CreateRawMonitor("agent data", &(gdata->lock));
	check_jvmti_error(jvmti, error, "Cannot create raw monitor");

	return JNI_OK;
}
