#!/bin/sh
###########################################################################
# MIT License                                                             #
# Copyright (c) 2020 AKIYAMA Isao                                         #
#                                                                         #
# Permission is hereby granted, free of charge, to any person obtaining   #
# a copy of this software and associated documentation files (the         #
# "Software"), to deal in the Software without restriction, including     #
# without limitation the rights to use, copy, modify, merge, publish,     #
# distribute, sublicense, and/or sell copies of the Software, and to      #
# permit persons to whom the Software is furnished to do so, subject to   #
# the following conditions:                                               #
#                                                                         #
# The above copyright notice and this permission notice shall be          #
# included in all copies or substantial portions of the Software.         #
#                                                                         #
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,         #
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF      #
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  #
# IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY    #
# CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,    #
# TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE       #
# SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.                  #
###########################################################################

not_setup() {
  echo "** ERROR: Not configured. Install directory missing."
  exit 1
}

bad_arg() {
  echo "** ERROR: Invalide argument. $1"
  exit 1
}

bad_mode() {
  echo "** ERROR: Invalide mode. $MODE"
  exit 1
}

target_already_exists_error() {
  echo "** ERROR: Install directory $INSTALL_DIR already exists. Delete it in advance."
  exit 1
}

target_does_not_exist_error() {
  echo "** ERROR: Install directory $INSTALL_DIR does not exist."
  exit 1
}

copy_error() {
  echo "** ERROR: Failed to copy files."
  exit 1
}

initial_install() {
  test ! -e $INSTALL_DIR || target_already_exists_error

  echo "mkdir $INSTALL_DIR"
  mkdir $INSTALL_DIR || copy_error

  install
}

upgrade_install() {
  # There may be add-in libraries under $INSTALL_DIR/lib; must keep them.

  test -e $INSTALL_DIR || target_does_not_exist_error

  echo "rm -f $INSTALL_DIR/*.txt"
  rm -f $INSTALL_DIR/*.txt

  echo "rm -Rf $INSTALL_DIR/src"
  rm -Rf $INSTALL_DIR/src

  echo "rm -Rf $INSTALL_DIR/lib/sgswing"
  rm -Rf $INSTALL_DIR/lib/sgswing

  echo "rm -Rf $INSTALL_DIR/lib/sni_sgswing"
  rm -Rf $INSTALL_DIR/lib/sni_sgswing

  echo "rm -Rf $INSTALL_DIR/doc"
  rm -Rf $INSTALL_DIR/doc

  echo "rm -Rf $INSTALL_DIR/sample"
  rm -Rf $INSTALL_DIR/sample

  install
}

install() {
  echo "cp ./*.txt $INSTALL_DIR/"
  cp ./*.txt $INSTALL_DIR/ || copy_error

  echo "cp -R ./src $INSTALL_DIR/"
  cp -R ./src $INSTALL_DIR/ || copy_error

  echo "cp -R ./lib $INSTALL_DIR/"
  cp -R ./lib $INSTALL_DIR/ || copy_error

  echo "cp -R ./doc $INSTALL_DIR/"
  cp -R ./doc $INSTALL_DIR/ || copy_error

  echo "cp -R ./sample $INSTALL_DIR/"
  cp -R ./sample $INSTALL_DIR/ || copy_error

  echo "find $INSTALL_DIR -type d | xargs chmod 755"
  find $INSTALL_DIR -type d | xargs chmod 755 || copy_error

  echo "find $INSTALL_DIR -type f | xargs chmod 644"
  find $INSTALL_DIR -type f | xargs chmod 644 || copy_error
}

# -- start --

INSTALL_DIR=_INSTALL_DIR_
MODE="initial"

test "$INSTALL_DIR" != "" || not_setup

while [ "$1" != "" ]; do
  if [ "$1" = "-mode" ]; then
    MODE=$2
    shift 2
  else
    bad_arg $1
  fi
done

if [ "$MODE" = "initial" ]; then
  initial_install
elif [ "$MODE" = "upgrade" ]; then
  upgrade_install
else
  bad_mode
fi

exit 0
