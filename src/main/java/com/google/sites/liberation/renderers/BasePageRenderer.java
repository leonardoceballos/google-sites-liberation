/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.sites.liberation.renderers;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.sites.liberation.util.EntryType.getType;
import static com.google.sites.liberation.util.EntryType.isPage;

import com.google.common.collect.Sets;
import com.google.gdata.data.sites.AttachmentEntry;
import com.google.gdata.data.sites.BaseContentEntry;
import com.google.gdata.data.sites.BasePageEntry;
import com.google.gdata.data.sites.CommentEntry;
import com.google.sites.liberation.util.EntryStore;
import com.google.sites.liberation.util.EntryUtils;
import com.google.sites.liberation.util.XmlElement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A basic implementation of PageRender that uses a 
 * {@code BasePageEntry} and an {@code EntryStore} to render a basic web 
 * page from a site.
 * 
 * @author bsimon@google.com (Benjamin Simon)
 * 
 * @param <T> the type of entry being rendered
 */
class BasePageRenderer<T extends BasePageEntry<?>> implements PageRenderer {

  protected final T entry;
  protected final EntryStore entryStore;
  protected Collection<BasePageEntry<?>> subpages;
  protected Collection<AttachmentEntry> attachments;
  protected Collection<CommentEntry> comments;
  protected XmlElementFactory elementFactory;
  
  /** 
   * Creates a new instance of BasePageRenderer.
   * 
   * @param entry the entry corresponding to this page
   * @param entryStore EntryStore containing this entry, its children, and its
   *                   parents if they exist
   */
  BasePageRenderer(T entry, EntryStore entryStore) {
    this.entry = checkNotNull(entry);
    this.entryStore = checkNotNull(entryStore);
    subpages = Sets.newTreeSet(new TitleComparator());
    attachments = Sets.newTreeSet(new UpdatedComparator());
    comments = Sets.newTreeSet(new UpdatedComparator());
    for(BaseContentEntry<?> child : entryStore.getChildren(entry.getId())) {
      addChild(child);
    }
    elementFactory = new XmlElementFactoryImpl();
  }
  
  /**
   * This method adds the given child to the correct collection. It is called
   * from the constructor and should be overridden for subclasses with
   * additional child types. 
   */
  protected void addChild(BaseContentEntry<?> child) {
    checkNotNull(child);
    switch(getType(child)) {
      case ATTACHMENT: attachments.add((AttachmentEntry) child); break;
      case COMMENT: comments.add((CommentEntry) child); break;
      default: 
        if (isPage(child)) {
          subpages.add((BasePageEntry<?>) child);
        }
    }
  }
  
  @Override
  public T getEntry() {
    return entry;
  }
  
  @Override
  public XmlElement renderAttachments() {
    if (attachments.size() == 0) {
      return null;
    }
    XmlElement div = new XmlElement("div");
    div.addElement(new XmlElement("hr"));
    XmlElement h4 = new XmlElement("h4");
    h4.addText("Attachments (" + attachments.size() + ")");
    div.addElement(h4);
    for(BaseContentEntry<?> attachment : attachments) {
      XmlElement attachmentDiv = elementFactory.getEntryElement(attachment, 
          "div");
      XmlElement link = new XmlElement("a").addElement(
          elementFactory.getTitleElement(attachment));
      String href = entry.getPageName().getValue() + "/" + 
          attachment.getTitle().getPlainText();
      link.setAttribute("href", href);
      XmlElement updated = elementFactory.getUpdatedElement(attachment);
      XmlElement author = elementFactory.getAuthorElement(attachment);
      XmlElement revision = elementFactory.getRevisionElement(attachment);
      attachmentDiv.addElement(link);
      attachmentDiv.addText(" - on ").addElement(updated);
      attachmentDiv.addText(" by ").addElement(author);
      attachmentDiv.addText(" (Version ").addElement(revision).addText(")");
      div.addElement(attachmentDiv);
    }
    return div;
  }
  
  @Override
  public XmlElement renderComments() {
    if (comments.size() == 0) {
      return null;
    }
    XmlElement div = new XmlElement("div");
    div.addElement(new XmlElement("hr"));
    XmlElement h4 = new XmlElement("h4");
    h4.addText("Comments (" + comments.size() + ")");
    div.addElement(h4);
    for(BaseContentEntry<?> comment : comments) {
      XmlElement commentDiv = elementFactory.getEntryElement(comment, "div");
      XmlElement author = elementFactory.getAuthorElement(comment);
      XmlElement updated = elementFactory.getUpdatedElement(comment);
      XmlElement revision = elementFactory.getRevisionElement(comment);
      XmlElement content = elementFactory.getContentElement(comment);
      commentDiv.addElement(author).addText(" - ").addElement(updated);
      commentDiv.addText(" (Version ").addElement(revision).addText(")");
      commentDiv.addElement(content);
      div.addElement(commentDiv);
    }
    return div;
  }

  @Override
  public XmlElement renderContent() {
    XmlElement div = new XmlElement("div");
    div.addText("Updated on ");
    div.addElement(elementFactory.getUpdatedElement(entry));
    div.addText(" by ");
    div.addElement(elementFactory.getAuthorElement(entry));
    div.addElement(elementFactory.getContentElement(entry));
    return div;
  }

  @Override
  public XmlElement renderParentLinks() {
    List<BaseContentEntry<?>> ancestors = new ArrayList<BaseContentEntry<?>>();
    BaseContentEntry<?> currentChild = entry;
    while(currentChild != null) {
      String parentId = EntryUtils.getParentId(currentChild);
      if (parentId == null) {
        currentChild = null;
      } else {
        currentChild = entryStore.getEntry(parentId);
        if (currentChild != null) {
          ancestors.add(currentChild);
        }
      }
    }
    if (ancestors.size() == 0) {
      return null;
    }
    XmlElement div = new XmlElement("div");
    for(int i = ancestors.size() - 1; i >= 0; i--) {
      BaseContentEntry<?> ancestor = ancestors.get(i);
      String path = "";
      for(int j = 0; j <= i; j++) {
        path += "../";
      }
      XmlElement link = elementFactory.getHyperLink(path + "index.html", 
          ancestor.getTitle().getPlainText());
      div.addElement(link);
      div.addText(" > ");
    }
    return div;
  }

  @Override
  public XmlElement renderAdditionalContent() {
    return null;
  }

  @Override
  public XmlElement renderSubpageLinks() {
    if (subpages.size() == 0) {
      return null;
    }
    XmlElement div = new XmlElement("div");
    div.addElement(new XmlElement("hr"));
    div.addText("Subpages (" + subpages.size() + "): ");
    boolean firstLink = true;
    for(BasePageEntry<?> subpage : subpages) {
      String href = subpage.getPageName().getValue() + "/index.html";
      if (!firstLink) {
        div.addText(", ");
      }
      div.addElement(elementFactory.getHyperLink(href, subpage.getTitle().getPlainText()));
      firstLink = false;
    }
    return div;
  }

  @Override
  public XmlElement renderTitle() {
    XmlElement title = new XmlElement("h3").addElement(
        elementFactory.getTitleElement(entry));
    return title;
  }
}
