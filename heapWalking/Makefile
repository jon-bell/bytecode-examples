UNAME := $(shell uname)
CCFLAGS = -o target/walkingExample.o -I${JAVA_HOME}/include -c -fPIC -fpermissive
ifeq ($(UNAME), Linux)
	CCFLAGS += -I${JAVA_HOME}/include/linux 
	LINKFLAGS = -z defs -static-libgcc -shared -o target/libwalkingExample.so -lc 
endif
ifeq ($(UNAME), Darwin)
	CCFLAGS += -I${JAVA_HOME}/include/darwin
	LINKFLAGS += -dynamiclib -o target/libwalkingExample.so
endif

libtaggingExample.dylib:
	gcc ${CCFLAGS} src/main/c/walker.cpp
	g++ ${LINKFLAGS} target/walkingExample.o
clean:
	rm -rf target/libwalkingExample.o target/libwalkingExample.dylib target/libwalkingExample.so
