import { expect, message, openChat, test } from './fixtures'

test('messages, typing and read receipts arrive live', async ({ createUser, signIn, directChat }) => {
  const alice = await createUser('Alice Marlowe')
  const bob = await createUser('Bob Hartley')
  const chat = await directChat(alice, bob, 'hello bob')

  const alicePage = await signIn(alice)
  const bobPage = await signIn(bob)
  await openChat(alicePage, chat)
  await openChat(bobPage, chat)
  await expect(message(bobPage, 'hello bob')).toBeVisible()

  // typing is only re-sent every couple of seconds, so keep typing until bob sees it
  await expect(async () => {
    await alicePage.locator('textarea').pressSequentially('are you around? ', { delay: 20 })
    await expect(bobPage.getByText('Alice Marlowe is writing')).toBeVisible({ timeout: 1500 })
  }).toPass({ timeout: 15_000 })
  await alicePage.locator('textarea').fill('are you around?')

  await alicePage.keyboard.press('Enter')
  await expect(message(bobPage, 'are you around?')).toBeVisible()
  // bob has the chat open, so alice's message turns read
  await expect(alicePage.getByLabel('read').last()).toBeVisible()
})

test('search finds a person and opens an empty chat with them', async ({ createUser, signIn }) => {
  const alice = await createUser('Alice Marlowe')
  const bob = await createUser('Bob Hartley')
  const page = await signIn(alice)

  await page.getByPlaceholder('Search').fill(bob.username)
  await page.getByRole('button', { name: /Bob Hartley/ }).click()

  await expect(page.getByText('A blank page.')).toBeVisible()
  await page.locator('textarea').fill('first line')
  await page.keyboard.press('Enter')
  await expect(page.locator('nav a', { hasText: 'Bob Hartley' })).toContainText('first line')
})

test('edits and deletes for everyone show up for the other person', async ({ createUser, signIn, directChat }) => {
  const alice = await createUser('Alice Marlowe')
  const bob = await createUser('Bob Hartley')
  const chat = await directChat(alice, bob, 'lunch at noon?')

  const alicePage = await signIn(alice)
  const bobPage = await signIn(bob)
  await openChat(alicePage, chat)
  await openChat(bobPage, chat)

  const mine = message(alicePage, 'lunch at noon?')
  await mine.hover()
  await mine.getByTitle('Edit').click()
  await alicePage.locator('textarea').fill('lunch at one?')
  await alicePage.keyboard.press('Enter')
  await expect(message(bobPage, 'lunch at one?')).toContainText('edited')

  const edited = message(alicePage, 'lunch at one?')
  await edited.hover()
  await edited.getByTitle('Delete').click()
  await alicePage.getByRole('button', { name: 'Delete for everyone' }).click()
  await expect(bobPage.getByText('This message was deleted.')).toBeVisible()
  await expect(alicePage.getByText('You deleted this message.')).toBeVisible()
})
