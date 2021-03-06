#!/bin/bash

#取当前目录
BASE_PATH=`cd "$(dirname "$0")"; pwd`

#设置java运行参数
DEFAULT_JAVA_OPTS=" -server -Xmx1g -Xms1g -Xmn256m -XX:PermSize=128m -Xss256k -XX:+DisableExplicitGC -XX:+UseConcMarkSweepGC -XX:+CMSParallelRemarkEnabled -XX:+UseCMSCompactAtFullCollection -XX:LargePageSizeInBytes=128m -XX:+UseFastAccessorMethods -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=70 "


#引入外部参数配置文件:
SPRING_CFG="/etc/default/spring"
if [ -f "$SPRING_CFG" ]; then
	. $SPRING_CFG
fi

#定义变量:
APP_PATH=${APP_PATH:-`dirname "$BASE_PATH"`}
CLASS_PATH=${CLASS_PATH:-$APP_PATH/config:$APP_PATH/lib/*}
JAVA_OPTS=${JAVA_OPTS:-$DEFAULT_JAVA_OPTS}
JAVA_OPTS=${JAVA_OPTS}${SPRING_OPTS}
MAIN_CLASS=${MAIN_CLASS:-"com.sogou.pay.notify.server.NotifyRunner"}

(echo $JAVA_OPTS | grep '\-Dspring.profiles.active=') || { echo 'spring.profiles.active not defined'; exit 1; }

DATE=`date +"%Y-%m-%d"`

LOGS_DIR=$APP_PATH/logs

if [ ! -d $LOGS_DIR ]; then
    mkdir $LOGS_DIR
fi

STDOUT_FILE=$LOGS_DIR/stdout.log.$DATE


exist(){
			if test $( pgrep -f "$MAIN_CLASS $APP_PATH" | wc -l ) -eq 0 
			then
				return 1
			else
				return 0
			fi
}

start(){
		if exist; then
				echo "Notify is already running."
				exit 1
		else
	    	cd $APP_PATH
				nohup java $JAVA_OPTS -cp $CLASS_PATH $MAIN_CLASS $APP_PATH >$STDOUT_FILE 2>&1 &
				echo "Notify is started."
		fi
}

stop(){
		runningPID=`pgrep -f "$MAIN_CLASS $APP_PATH"`
		if [ "$runningPID" ]; then
				echo "Notify pid: $runningPID"
        count=0
        kwait=5
        echo "Notify is stopping, please wait..."
        kill -15 $runningPID
					until [ `ps --pid $runningPID 2> /dev/null | grep -c $runningPID 2>/dev/null` -eq '0' ] || [ $count -gt $kwait ]
		        do
		            sleep 1
		            let count=$count+1;
		        done

	        if [ $count -gt $kwait ]; then
	            kill -9 $runningPID
	        fi
        clear
        echo "Notify is stopped."
    else
    		echo "Notify has not been started."
    fi
}

check(){
   if exist; then
   	 echo "Notify is alive."
   	 exit 0
   else
   	 echo "Notify is dead."
   	 exit -1
   fi
}

restart(){
        stop
        start
}

case "$1" in

start)
        start
;;
stop)
        stop
;;
restart)
        restart
;;
check)
        check
;;
*)
        echo "available operations: [start|stop|restart|check]"
        exit 1
;;
esac