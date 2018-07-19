# dcrandroid - Decred Mobile Wallet

[![Build Status](https://travis-ci.org/decred/dcrandroid.svg?branch=master)](https://travis-ci.org/decred/dcrandroid)

A Decred Mobile Wallet for android that runs on top of [dcrwallet](https://github.com/decred/dcrwallet).

## Requirements

Android 3.0 or above.

### Prerequisites

1. [Android SDK](https://developer.android.com/sdk/download.html) and [NDK](https://developer.android.com/ndk/downloads/index.html)
2. [Android Studio](https://developer.android.com/studio/index.html)
3. [Go(1.8 or 1.9)](http://golang.org/doc/install)
4. [Dep](https://github.com/golang/dep/releases)
5. [Gomobile](https://github.com/golang/go/wiki/Mobile#tools) (correctly init'd with gomobile init)

Run the following commands

    git clone https://github.com/decred/dcrandroid.git
    mkdir -p go/bin
    mkdir -p go/src/github.com/raedahgroup/mobilewallet
    git clone github.com/raedahgroup/mobilewallet go/src/github.com/raedahgroup/mobilewallet
    export GOPATH=$(pwd)/go
    export PATH=$PATH:$GOPATH/bin
    cd go/src/github.com/raedahgroup/mobilewallet
    dep ensure -v
    gomobile bind -target=android/arm
    cp mobilewallet.aar $GOPATH/../dcrandroid/app/libs/mobilewallet.aar
    cd $GOPATH/../dcrandroid
    ./gradlew