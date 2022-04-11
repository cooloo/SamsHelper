package com.egogame.samshelper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.Builder
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.log

class DingDongService : AccessibilityService() {
    var currentClassName: String = ""
    var chooseTimeSuccess: Boolean = false
    var enableJumpCart: Boolean = true
    var checkNotificationCount: Int = 0;
    var needBreakPay:Boolean=false
    var needAutoPay:Boolean=true
    lateinit var allActivities:ArrayList<String>;

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
//        Log.d(TAG, "event: $event")
        //doAccess(event)
        doNewAccess(event)
    }

    private fun doNewAccess(event: AccessibilityEvent?){
        //Log.d(TAG, "event: $event")
        event?.let {
            needAutoPay=false
            handleClassName(event)
            //getAllActivity(event)
            //Log.d(TAG, "CurrentClassName: $currentClassName")
            when(currentClassName){
                HOME_ACTIVITY->{
                    //检测弹框报错
                    if(checkPayAlert()) return
                    if(checkCart()) return
                }
                ALERT_ACTIVITY->{
                    //检测弹框报错
                    if(checkPayAlert()) return
                }
                CART_ACTIVITY->{
                    needAutoPay=true
                    //检测弹框报错
                    if(checkPayAlert()) return
                    //检测最终支付
                    if(checkFinalPay()) return
                    //检测预支付
                    if(checkPrePay()) return
                }
                PAY_ACTIVITY->{
                    //检测最终支付
                    if(checkFinalPay()) return
                }
            }

        }
    }

    private fun getAllActivity(event: AccessibilityEvent){
        var packageInfo: PackageInfo;
        try {
            Log.d(TAG,"event.packageName:"+event.packageName)
            packageInfo = packageManager.getPackageInfo(
                event.packageName.toString(), PackageManager.GET_ACTIVITIES);
            //所有的Activity
            var activities = packageInfo.activities;

            allActivities=ArrayList<String>()
            activities.forEach { info->
                allActivities.add(info.targetActivity)

                Log.d(TAG,"info.targetActivity:"+info.targetActivity)
            }

        } catch (e:Exception) {
            Log.d(TAG,"Get All Activity Error:"+e)
        }
    }

    private fun stopService(){
        needAutoPay=false
    }

    private fun checkCart():Boolean{
        //已经跳转了，不用再跳了
        var checkNode=findNode(rootInActiveWindow,"确认订单")
        if(checkNode!=null){
            return false
        }

        var node=findNode(rootInActiveWindow,"结算")
        if(node!=null){
            Log.d(TAG,"Click Cart")
            performAction(node)

            return true
        }
        return false
    }

    private fun checkPayAlert():Boolean{
        var node=findNode(rootInActiveWindow,"我知道了")
        if(node!=null){
            //Log.d(TAG,"Click Alert Known")
            performAction(node)
            return true
        }
        var retryNode=findNode(rootInActiveWindow,"购物火爆")
        if(retryNode==null) retryNode=findNode(rootInActiveWindow,"不合法")
        if(retryNode==null) retryNode=findNode(rootInActiveWindow,"返回修改")
        if(retryNode!=null){
            var retryBtnNode=findNode(rootInActiveWindow,"重试")
            if(retryBtnNode!=null){
                performAction(retryBtnNode)
                Log.d(TAG,"Click Alert Retry")
                return true
            }
        }

        return false
    }

    private fun checkFinalPay():Boolean{
        var doSuccess:Boolean=false
        var node=findNode(rootInActiveWindow,"选择支付方式")
        if(node!=null){
            var optionNode=findNode(rootInActiveWindow,"支付宝")
            if(optionNode!=null){
                Log.d(TAG,"Click Option")
                performAction(optionNode.parent.parent)
                doSuccess=true

                var payNode=findNode(rootInActiveWindow,"确认支付")
                if(payNode!=null){
                    performAction(payNode)
                    Log.d(TAG,"Click Final Pay")

                    doSuccess=true
                }
            }
            if(doSuccess) return true
        }
        return false
    }

    private fun checkPrePay():Boolean{
        GlobalScope.launch {
            delay(1000)
        }

        var success:Boolean=false
        var node=findNode(rootInActiveWindow,"去支付")
        if(node!=null){
            success = true
            //printNode(rootInActiveWindow,"root node")
            //return false
        }
        GlobalScope.launch {
            while (true){
                if(node!=null) {
                    clickNode(node)
                    //performAction(node)
                    //performActionWithChildren(node.parent)
                    Log.d(TAG,"Click Pre pay")
                }
                if(!needAutoPay) break
                delay(500)
            }
        }

        return success
    }

    private class MyCallBack:GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            super.onCompleted(gestureDescription)

            //Log.d(TAG,"Click Complete")
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
            super.onCancelled(gestureDescription)

            //Log.d(TAG,"Click Cancel")
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo){
        var rect:Rect = Rect();
        node.getBoundsInScreen(rect);
        var x = (rect.left + rect.right) / 2;
        var y = (rect.top + rect.bottom) / 2;

        var path: Path = Path()
        path.moveTo(x.toFloat(), y.toFloat());//滑动起点
        //path.lineTo(2000f, 1000f);//滑动终点
        var builder:Builder = Builder();
        var description = builder.addStroke(GestureDescription.StrokeDescription(path, 100L, 100L)).build();
        //100L 第一个是开始的时间，第二个是持续时间
        var isDispatched=dispatchGesture(description, MyCallBack(), null);

        //Log.d(TAG,"isDispatched:"+isDispatched+"==="+x+"=="+y)
    }

    private fun doAccess(event: AccessibilityEvent?){
        event?.let {
            handleClassName(event)
            if(currentClassName!= CART_ACTIVITY) needBreakPay=true
            when (currentClassName) {
                CART_ACTIVITY -> {
                    pay(event)
                }
                HOME_ACTIVITY -> {
                    jumpToCartActivity(event)
                }
                PAY_ACTIVITY -> {
                    finalPay(event)
                }
                CHOOSE_DELIVERY_TIME, CHOOSE_DELIVERY_TIME_V2 -> {
                    chooseDeliveryTime(event)
                }
                GX0 -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                XN1 -> {
                    checkNotification(event)
                }
                ALERT_ACTIVITY -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                RETURN_CART_DIALOG -> {
                    clickReturnCartBtn(event)
                }
                else -> {
                    clickDialog(event)
                }
            }
        }
    }

    private fun handleClassName(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        currentClassName = event.className as String
        Log.d(TAG, "currentClassName: $currentClassName")
        if (currentClassName in listOf(CART_ACTIVITY, CHOOSE_DELIVERY_TIME, CHOOSE_DELIVERY_TIME_V2)) {
            enableJumpCart = true
        }
        if (currentClassName == HOME_ACTIVITY) {
            checkNotificationCount = 0
        }
    }

    private fun clickReturnCartBtn(event: AccessibilityEvent) {
        var nodes = event.source?.findAccessibilityNodeInfosByText("返回购物车")
        nodes?.forEach { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "clickDialog confirm")
            return@forEach
        }
    }

    private fun checkNotification(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (checkNotificationCount ++ > 1) {
                Log.d(TAG, "checkNotificationCount: $checkNotificationCount, return")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } else {
            checkNotificationCount = 0
        }
    }

    private fun pay(event: AccessibilityEvent) {
        Log.d(TAG, "event.eventType:"+event.toString())
        if(event==null) return
        if(event.eventType==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED){
            needBreakPay=true
        }
        //确认当前界面上面有支付选项，跳过处理
        var paynode = findNode(event,"选择支付方式")
        if(paynode!=null){
            finalPay(event)
            return
        }

        var retryNode=findNode(event,"重试")
        if(retryNode!=null){
            retryNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "onClick retry")
            return
        }

        var payNode = findNode(event,"去支付")
        if(payNode!=null){
            GlobalScope.launch {
                while (true){
                    delay(500)
                    performAction(payNode)
                    Log.d(TAG, "onClick pay button")

                    if(currentClassName!= CART_ACTIVITY) break
                    //printNode(payNode,"pay node")
                    //printNode(payNode.parent,"pay node parent")
                    //performAction(payNode.parent)
                    //Log.d(TAG, "needBreakPay is "+needBreakPay)
//                    if(needBreakPay){
//                        needBreakPay=false
//                        break
//                    }
                }
            }
        }
    }

    private fun findNode(event: AccessibilityEvent,text:String): AccessibilityNodeInfo? {
        if(event==null || event.source==null) return null
        return findNode(event.source,text)
    }

    private fun findNode(node: AccessibilityNodeInfo?,text:String): AccessibilityNodeInfo? {
        try {
            if(node==null) return null
            var results = node?.findAccessibilityNodeInfosByText(text)
            if(results!=null && results.size>0){
                return results[0]
            }
            return null
        }catch (e:Exception){
            return null
        }
    }

    private fun finalPay(event: AccessibilityEvent) {
        if(event==null) return
        //Log.d(TAG, "needHandleClassName +"+needHandleClassName+"=="+currentClassName)
        //Log.d(TAG, "event.eventType:"+event.toString())
        //if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        var nodes = event.source?.findAccessibilityNodeInfosByText("支付宝")
        var payBtnNodes=event.source?.findAccessibilityNodeInfosByText("确认支付")

        if(nodes!=null && nodes.size>0 && payBtnNodes!=null && payBtnNodes.size>0){
            GlobalScope.launch {
                delay(100)
            }
            performAction(nodes[0].parent.parent)

            performAction(payBtnNodes)
        }
        return
        Log.d(TAG, "select pay alipay nodes+"+nodes+"=="+payBtnNodes)
        nodes?.forEach { node ->
            //printNode(node.parent.parent,"final pay")
            //setCheckNode(node.parent.parent)
            printNode("option node 1",node)
            printNode("option node 2",node.parent)
            printNode("option node 3",node.parent.parent)
            node.parent.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            Log.d(TAG, "select pay alipay")

            var payBtnNodes=event.source?.findAccessibilityNodeInfosByText("确认支付")
//            Log.d(TAG, "start confirm pay:"+payBtnNodes)
            payBtnNodes?.forEach { node ->
                printNode("confirm node",node.parent)
                node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
//                node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
//                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                Log.d(TAG, "confirm pay")
            }
        }
    }

    private fun performAction(nodes:List<AccessibilityNodeInfo>){
        nodes?.forEach { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun performAction(node:AccessibilityNodeInfo){
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun performActionWithChildren(node:AccessibilityNodeInfo?){
        if(node==null) return
        for (i in 0 until node.childCount) {
            var n=node.getChild(i)

            if(n==null) continue
            n.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            Log.d(TAG,"click node:"+n.toString())

            performActionWithChildren(n)
        }
    }

    private fun printNode(key: String,node: AccessibilityNodeInfo){
        Log.d(TAG, key+"detail:"+node.toString())
    }

    private fun printNode(node: AccessibilityNodeInfo,key:String){
        Log.d(TAG, "print node detail "+key+"==="+node.toString())
        for (i in 0 until node.childCount) {
            var n=node.getChild(i)

            Log.d(TAG, "node detail:"+n.toString())

            printNode(n,"i:"+i)
        }
    }

    private fun setCheckNode(node: AccessibilityNodeInfo){
        for (i in 0 until node.childCount) {
            var n=node.getChild(i)

            if(n.isCheckable){
                if(n.isChecked) {
                    break
                }
                GlobalScope.launch {
                    while (true){
                        delay(100)
                        n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if(n.isChecked) {
                            break
                        }
                    }
                }
            }
            setCheckNode(n)
        }
    }

    private fun chooseDeliveryTime(event: AccessibilityEvent) {
        Log.d(TAG, "chooseDeliveryTime: ${event.source}")
        var nodes = event.source?.findAccessibilityNodeInfosByText("-")
        nodes?.forEach { node ->
            if (node.parent.isEnabled) {
                chooseTimeSuccess = true
                node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "onClick chooseDeliveryTime")
                return@forEach
            }
        }
        if (!chooseTimeSuccess) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            GlobalScope.launch {
                delay(100)
                if (currentClassName in listOf(CHOOSE_DELIVERY_TIME, CHOOSE_DELIVERY_TIME_V2)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        }
    }

    private fun jumpToCartActivity(event: AccessibilityEvent) {
//        if (!enableJumpCart) {
//            Log.d(TAG, "enableJumpCart: $enableJumpCart, return")
//            return
//        }
        var payNode = findNode(event,"结算")
        if(payNode!=null){
            payNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            enableJumpCart = false
            Log.d(TAG, "onClick jump to cart")
        }
    }

    private fun clickDialog(event: AccessibilityEvent) {
        var nodes = event.source?.findAccessibilityNodeInfosByText("继续支付")
        if (nodes == null) {
            nodes = event.source?.findAccessibilityNodeInfosByText("修改送达时间")
        }
        nodes?.forEach { node ->
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "clickDialog confirm")
            return@forEach
        }
    }

    override fun onInterrupt() {
        stopService()
    }

    companion object {
        const val TAG = "SamsHelper"
        const val HOME_ACTIVITY = "cn.samsclub.app.ui.MainActivity"
        const val CART_ACTIVITY = "cn.samsclub.app.settle.SettlementActivity"
        const val PAY_ACTIVITY = "androidx.appcompat.app.e"
        const val CHOOSE_DELIVERY_TIME = "gy"
        const val GX0 = "gx0"
        const val ALERT_ACTIVITY = "com.tencent.srmsdk.dialog.base.BaseDialog"
        const val RETURN_CART_DIALOG = "by"
        const val XN1 = "xn1"
        const val CHOOSE_DELIVERY_TIME_V2 = "iy"
    }
}