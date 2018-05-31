package sh.calaba.instrumentationbackend.actions.text;

import android.os.Handler;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.webkit.WebView;
import sh.calaba.instrumentationbackend.Result;
import sh.calaba.instrumentationbackend.actions.Action;
import sh.calaba.instrumentationbackend.query.ast.UIQueryUtils;

public abstract class TextAction implements Action {
    @Override
    public final Result execute(String... args) {
        try {
            parseArguments(args);
        } catch (IllegalArgumentException e) {
            return Result.failedResult(e.getMessage());
        }

        final View servedView;

        try {
            servedView = InfoMethodUtil.getServedView();
        } catch (InfoMethodUtil.UnexpectedInputMethodManagerStructureException e) {
            e.printStackTrace();
            return Result.failedResult(e.getMessage());
        }

        if (servedView == null) {
            return Result.failedResult(getNoFocusedViewMessage());
        }

        if (servedView instanceof WebView) {
            return doWebViewInput((WebView) servedView);
        } else {
            return doNormalInput(servedView);
        }


    }

    private Result doWebViewInput(WebView servedView) {
        // FIXME: do JS based webview input, seems to be what espresso is also doing.
        // See fx. 'git clone https://android.googlesource.com/platform/frameworks/testing'
        // and checkout 'android-support-test' branch.
        // Notes: https://developer.android.com/training/testing/espresso/web
        // Code example: https://android.googlesource.com/platform/frameworks/testing/+/android-support-test/espresso/sample/src/androidTest/java/android/support/test/testapp/WebViewTest.java#100
        return Result.failedResult("WebView input is currently not supported. Found: " + servedView);
    }

    private Result doNormalInput(View servedView) {
        InputConnection inputConnection = InfoMethodUtil.getInputConnection(servedView);

        if (inputConnection == null) {
            return Result.failedResult("View does not support input: " + servedView);
        }

        FutureTask<Result> futureResult = new FutureTask<Result>(new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                return executeOnInputThread(servedView, inputConnection);
            }
        });

        UIQueryUtils.postOnViewHandlerOrUiThread(servedView, futureResult);

        try {
            return futureResult.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException executionException) {
            throw new RuntimeException(executionException.getCause());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract void parseArguments(String... args) throws IllegalArgumentException;
    protected abstract String getNoFocusedViewMessage();

    /*
        This method is run on the main thread.
     */
    protected abstract Result executeOnInputThread(final View servedView, final InputConnection inputConnection);
}
