import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const injectedPath = path.resolve(__dirname, '../injected.js');
const injectedCode = fs.readFileSync(injectedPath, 'utf8');

class FakeElement {
  constructor(tagName) {
    this.tagName = String(tagName || 'div').toUpperCase();
    this.children = [];
    this.parentNode = null;
    this.style = {};
    this.id = '';
    this.textContent = '';
  }

  appendChild(child) {
    child.parentNode = this;
    this.children.push(child);
    return child;
  }

  removeChild(child) {
    const index = this.children.indexOf(child);
    if (index >= 0) {
      this.children.splice(index, 1);
      child.parentNode = null;
    }
    return child;
  }

  remove() {
    if (this.parentNode) {
      this.parentNode.removeChild(this);
    }
  }

  get childElementCount() {
    return this.children.length;
  }
}

function findById(node, id) {
  if (!node) {
    return null;
  }
  if (node.id === id) {
    return node;
  }
  for (const child of node.children) {
    const found = findById(child, id);
    if (found) {
      return found;
    }
  }
  return null;
}

const fakeDocument = {
  body: new FakeElement('body'),
  documentElement: new FakeElement('html'),
  createElement: (tagName) => new FakeElement(tagName),
  getElementById(id) {
    return findById(this.body, id) || findById(this.documentElement, id);
  },
};

const sandbox = {
  URL,
  Headers,
  Response,
  document: fakeDocument,
  setTimeout: () => 1,
  console: {
    log: (...args) => console.log(...args),
    warn: (...args) => console.warn(...args),
    error: (...args) => console.error(...args),
  },
  window: {
    location: { href: 'https://www.zhihu.com/' },
    setTimeout: () => 1,
    fetch: async () =>
      new Response(JSON.stringify({ data: [] }), {
        headers: { 'content-type': 'application/json' },
      }),
  },
};

sandbox.window.window = sandbox.window;
vm.createContext(sandbox);
vm.runInContext(injectedCode, sandbox, { filename: injectedPath });

const core = sandbox.window.__zhihuFeedAdFilterCore__;
assert.ok(core, 'window 上应存在核心 API');

const { checkForAd, filterRecommendPayload } = core;

assert.equal(
  checkForAd({ type: 'feed', target: { type: 'answer', paid_info: { id: 1 }, content: 'normal' } }),
  true,
  'paid_info 应判为广告'
);

assert.equal(
  checkForAd({ type: 'feed', target: { type: 'article', content: '<a href="https://xg.zhihu.com">ad</a>' } }),
  true,
  'xg.zhihu.com 应判为广告链接'
);

assert.equal(
  checkForAd({ type: 'feed', target: { type: 'pin', content_html: '<a href="https://mp.weixin.qq.com/s/abc">wx</a>' } }),
  true,
  'mp.weixin.qq.com 应判为广告链接'
);

assert.equal(
  checkForAd({ type: 'feed_advert', action_text: 'promoted' }),
  true,
  'feed_advert 应判为广告'
);

assert.equal(
  checkForAd({ type: 'feed', target: { type: 'answer', content: '<p>normal content</p>' } }),
  false,
  '普通条目不应被过滤'
);

const payload = {
  data: [
    { type: 'feed', target: { type: 'answer', content: 'clean' } },
    {
      type: 'feed',
      target: {
        type: 'answer',
        paid_info: { sku: 'vip' },
        content: 'paid',
        question: { title: '付费回答标题' },
      },
    },
    {
      type: 'feed',
      target: {
        type: 'article',
        title: '学堂文章标题',
        content: '<a href="https://d.zhihu.com">school</a>',
      },
    },
  ],
  paging: {
    totals: 20,
  },
};

const filteredPayload = filterRecommendPayload(payload);
assert.ok(filteredPayload, '含广告的响应载荷应被改写');
assert.equal(filteredPayload.data.length, 1, '应只保留一个非广告条目');
assert.equal(filteredPayload.paging.totals, 18, 'paging.totals 应减去已移除条目数');

const toastContainer = fakeDocument.getElementById('zhihu-ad-filter-toast-container');
assert.ok(toastContainer, '应创建提示容器');
assert.equal(toastContainer.children.length, 2, '每个被过滤条目应显示一条提示');

const firstToast = toastContainer.children[0];
const secondToast = toastContainer.children[1];
assert.equal(firstToast.style.background, '#ffffff', '提示背景应为白色');

const firstIcon = firstToast.children[0];
const firstText = firstToast.children[1];
const secondText = secondToast.children[1];

assert.equal(firstIcon.textContent, '✓', '提示图标应为对勾');
assert.equal(firstIcon.style.color, '#16a34a', '提示对勾应为绿色');
assert.ok(firstText.textContent.includes('付费回答标题'), '第一条提示应包含被过滤回答的标题');
assert.ok(secondText.textContent.includes('学堂文章标题'), '第二条提示应包含被过滤文章的标题');

assert.equal(
  filterRecommendPayload({ data: [{ type: 'feed', target: { type: 'answer', content: 'clean' } }] }),
  null,
  '不含广告的响应载荷不应被改写'
);

console.log('知乎广告过滤测试通过');
